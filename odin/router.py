import json
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from openai import OpenAI
from odin.tools import tools, web_search, vector_search, metadata, describe_photo, file_search, read_file, read_webpage
from odin.tools.tools import available_tools
from dotenv import load_dotenv
import os

load_dotenv()
API_KEY = os.get_env("API_KEY")

router = APIRouter()

client = OpenAI(base_url="http://127.0.0.1:8000", api_key="none")

@router.websocket("/chat/{chat_id}")
async def chat_socket(ws: WebSocket, chat_id: str | None = None):
    user_key = ws.headers.get("X-Api-Key")
    if user_key != API_KEY:
        await ws.close(code=1008)
        return False

    await ws.accept()
    if(chat_id == None): chat_id = create_new_chat()
    messages = get_chat_history(chat_id)
    try:
        while True:
            incoming = await ws.receive_json()
            #messages.append(incoming["message"])

            await send_model_message(ws, client, messages, chat_id, incoming["message"])

            await ws.send_json({"message":"end"})
        
    except WebSocketDisconnect:
        print("Disconnected!")

def get_chat_history(chat_id: str):

    return []

def create_new_chat():

    return ""

def save_message(chat_id: str, role: str, content: str):
    # TODO: store in DB
    print(f"[SAVE] {chat_id} | {role} | {content}")

async def send_model_message(ws, client, messages, chat_id, user_text):
    messages.append({"role": "user", "content": user_text})
    save_message(chat_id, "user", user_text)

    stream = client.chat.completions.create(
        model="gemma-3-12b-it-Q4_K_M.gguf",
        messages=messages,
        tools=available_tools,
        stream=True
    )

    final_response = ""

    for chunk in stream:
        choice = chunk.choices[0]

        if choice.delta and choice.delta.tool_calls:
            tool_call = choice.delta.tool_calls[0]
            fn_name = tool_call.function.name
            args = tool_call.function.arguments

            result_json = run_tool(fn_name, args)

            messages.append({
                "role": "tool",
                "name": fn_name,
                "content": result_json
            })

            continue

        if choice.delta and choice.delta.content:
            token = choice.delta.content
            final_response += token
            await ws.send_json({
                "type": "token",
                "chat_id": chat_id,
                "text": token
            })

    messages.append({
        "role": "assistant",
        "content": final_response
    })

    await ws.send_json({
        "type": "done",
        "chat_id": chat_id
    })


def run_tool(name, args_json):
    args = json.loads(args_json)

    if name == "file_search":
        return file_search(**args)

    if name == "vector_search":
        return vector_search(**args)

    if name == "read_file":
        return read_file(**args)

    if name == "describe_photo":
        return describe_photo(**args)

    if name == "get_metadata":
        return metadata(**args)

    if name == "web_search":
        return web_search(**args)

    if name == "read_webpage":
        return read_webpage(**args)

    return "{}"
