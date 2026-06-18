import os
import sqlite3
import time
from collections import defaultdict
from fastapi import FastAPI, Request, Response, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv

# Import routers and components
from mimir.orion.router import router as orion_router
from odin.router import router as odin_router
from mimir.router import router as mimir_router
from heimdall.router import router as heimdall_router
from odin.start_llm_server import start_llama_server

# Import authentication services
from bifrost.auth import (
    verify_session_token,
    get_user_by_email,
    create_magic_token,
    decode_magic_token,
    create_session,
    create_user,
    check_and_create_share_guest_user
)
from bifrost.email_service import get_email_provider
from bifrost.audit import log_audit

load_dotenv()
API_KEY = os.getenv("API_KEY", "koala")

app = FastAPI(title="L.O.R.E Gateway")

# --- Custom Dynamic CORS Middleware ---
@app.middleware("http")
async def cors_middleware(request: Request, call_next):
    if request.method == "OPTIONS":
        response = Response(status_code=200)
    else:
        response = await call_next(request)

    origin = request.headers.get("origin")
    if origin:
        allowed_origins_raw = os.getenv("ALLOWED_ORIGINS", "")
        if allowed_origins_raw:
            allowed_origins = [o.strip() for o in allowed_origins_raw.split(",") if o.strip()]
        else:
            allowed_origins = ["*"]

        if "*" in allowed_origins or origin in allowed_origins:
            response.headers["Access-Control-Allow-Origin"] = origin if "*" not in allowed_origins else "*"
            if "*" in allowed_origins:
                response.headers["Access-Control-Allow-Credentials"] = "false"
            else:
                response.headers["Access-Control-Allow-Credentials"] = "true"
            response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS, HEAD"
            response.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type, X-Api-Key"
            response.headers["Access-Control-Max-Age"] = "600"

    return response

# --- In-Memory Rate Limiter ---
RATE_LIMIT_STORE = defaultdict(list)

def check_rate_limit(ip: str, group: str, limit: int, window: int = 60) -> bool:
    now = time.time()
    cutoff = now - window
    RATE_LIMIT_STORE[(ip, group)] = [t for t in RATE_LIMIT_STORE[(ip, group)] if t > cutoff]
    if len(RATE_LIMIT_STORE[(ip, group)]) >= limit:
        return False
    RATE_LIMIT_STORE[(ip, group)].append(now)
    return True

@app.middleware("http")
async def rate_limiting_middleware(request: Request, call_next):
    ip = request.client.host if request.client else "unknown"
    path = request.url.path
    
    if path.startswith("/auth/"):
        if not check_rate_limit(ip, "auth", limit=10):
            log_audit(None, "rate_limit_exceeded", path, ip, "failed")
            return Response("Too Many Requests", status_code=429)
    elif path.startswith("/s/"):
        if not check_rate_limit(ip, "share", limit=60):
            log_audit(None, "rate_limit_exceeded", path, ip, "failed")
            return Response("Too Many Requests", status_code=429)
    elif path == "/mimir/upload" or path.startswith("/mimir/upload/"):
        if not check_rate_limit(ip, "upload", limit=20):
            log_audit(None, "rate_limit_exceeded", path, ip, "failed")
            return Response("Too Many Requests", status_code=429)
            
    return await call_next(request)

# --- Security Headers and Content-Disposition Middleware ---
@app.middleware("http")
async def security_headers_middleware(request: Request, call_next):
    response = await call_next(request)
    
    # 1. Security Headers
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    response.headers["Content-Security-Policy"] = (
        "default-src 'self'; "
        "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; "
        "style-src 'self' 'unsafe-inline'; "
        "img-src 'self' data:; "
        "frame-src 'self';"
    )

    # 2. Content-Disposition: attachment for non-preview files to block XSS via SVG/HTML uploads
    path = request.url.path
    is_file_serving_path = (
        path.startswith("/mimir/download/") or 
        path.startswith("/orion/media/") or 
        (path.startswith("/s/") and ("/download/" in path or "/view/" in path or "/file/" in path))
    )
    if is_file_serving_path:
        content_type = response.headers.get("content-type", "").lower()
        preview_types = {
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/bmp",
            "application/pdf",
            "video/mp4", "video/webm", "video/ogg"
        }
        if content_type and not any(pt in content_type for pt in preview_types):
            cd = response.headers.get("content-disposition")
            if cd:
                if "inline" in cd:
                    response.headers["content-disposition"] = cd.replace("inline", "attachment")
            else:
                response.headers["content-disposition"] = "attachment"

    return response

# --- Authentication and Authorization Middleware ---
@app.middleware("http")
async def gatekeeper_middleware(request: Request, call_next):
    # Bypass auth for health-check, public verification and sharing portal endpoints
    if request.url.path in ["/", "/auth/request-link", "/auth/verify"] or request.url.path.startswith("/s/"):
        return await call_next(request)

    user_api_key = request.headers.get("X-Api-Key")
    authorized = False
    user_id = None
    email = None
    is_owner = False

    # 1. Try static API key check
    if user_api_key == API_KEY:
        authorized = True
        is_owner = True
        user_id = 1
        email = "owner@lore.local"
    else:
        # 2. Try Bearer Session Token check
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            token = auth_header[7:]
            user = verify_session_token(token)
            if user:
                authorized = True
                user_id = user["id"]
                email = user["email"]
                is_owner = bool(user["is_owner"])

    if not authorized:
        log_audit(None, "unauthorized_access", request.url.path, request.client.host if request.client else "unknown", "failed")
        return Response("Unauthorized", status_code=401)

    # Store user identity in request state
    request.state.user_id = user_id
    request.state.email = email
    request.state.is_owner = is_owner

    return await call_next(request)


# Auto-run DB migrations on application startup
@app.on_event("startup")
def startup_db_migrations():
    from mimir.saraswati.init import run_migrations
    run_migrations()


# Include routers
app.include_router(orion_router, prefix="/orion")
app.include_router(odin_router, prefix="/odin")
app.include_router(mimir_router, prefix="/mimir")
app.include_router(heimdall_router)  # Served at root to catch /s/{token}


# Pydantic Schemas for Auth Router
class RequestLinkPayload(BaseModel):
    email: str


@app.post("/auth/request-link")
def request_link(payload: RequestLinkPayload, request: Request):
    email = payload.email.strip().lower()
    if "@" not in email or "." not in email:
        raise HTTPException(status_code=400, detail="Invalid email address")
    
    ip = request.client.host if request.client else "unknown"

    # Look up user in DB
    user = get_user_by_email(email)
    if not user:
        if check_and_create_share_guest_user(email):
            user = get_user_by_email(email)

    if not user:
        # Auto-create the very first user as owner if DB is empty
        from constants import DB_PATH
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM users")
        count = cursor.fetchone()[0]
        conn.close()
        
        if count == 0:
            user = create_user(email, email.split("@")[0], is_owner=True)
            print(f"Auto-created first owner user during magic-link request: {email}")
        else:
            log_audit(None, "auth_request_link", email, ip, "failed")
            raise HTTPException(status_code=403, detail="Email address not registered or invited")

    # Generate JWT verification token
    token = create_magic_token(email)
    
    # Construct verification link
    base_url = str(request.base_url).rstrip("/")
    verify_url = f"{base_url}/auth/verify?token={token}"
    
    # Send magic link email
    provider = get_email_provider()
    success = provider.send_magic_link(email, verify_url)
    
    if not success:
        log_audit(user["id"], "auth_request_link", email, ip, "failed")
        raise HTTPException(status_code=500, detail="Failed to dispatch magic link email")
        
    log_audit(user["id"], "auth_request_link", email, ip, "success")
    return {"status": "ok", "message": "Magic link sent"}


@app.get("/auth/verify")
def verify_link(token: str, request: Request, device_label: str = "Web/App Client"):
    ip = request.client.host if request.client else "unknown"
    email = decode_magic_token(token)
    if not email:
        log_audit(None, "auth_login", None, ip, "failed")
        raise HTTPException(status_code=401, detail="Invalid or expired magic link token")
        
    user = get_user_by_email(email)
    if not user:
        log_audit(None, "auth_login", email, ip, "failed")
        raise HTTPException(status_code=401, detail="User account no longer exists")
        
    # Generate and store a new hashed session
    session_token = create_session(user["id"], device_label)
    
    log_audit(user["id"], "auth_login", email, ip, "success")
    return {
        "status": "ok",
        "session_token": session_token,
        "user": user
    }


@app.get("/")
def check():
    return {"status": "ok"}