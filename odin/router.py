import json
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from openai import OpenAI
from odin.tools.tools import available_tools
from odin.tools.web_search import web_search
from odin.tools.read_webpage import read_webpage
from dotenv import load_dotenv
import os

load_dotenv()
API_KEY = os.getenv("API_KEY")

router = APIRouter()

client = OpenAI(base_url="http://127.0.0.1:8000", api_key="none")

@router.websocket("/chat/{chat_id}")
async def chat_socket(ws: WebSocket, chat_id: str | None = None):
    user_key = ws.headers.get("X-Api-Key") or ws.query_params.get("x-api-key")

    if user_key != API_KEY:
        await ws.close(code=1008)
        return False

    await ws.accept()
    
    if(chat_id == None): chat_id = create_new_chat()
    messages = get_chat_history(chat_id)
    
    try:
        while True:
            incoming = await ws.receive_json()

            message_id = incoming["id"]
            prompt = incoming["prompt"]
            # attachments = incoming["attachments"]

            await send_model_message(ws, client, messages, chat_id, message_id, prompt)
            await ws.send_json({
                "type": "UPDATE",
                "text": "end",
                "chat_id": chat_id,
                "message_id": message_id
            })
        
    except WebSocketDisconnect:
        print("Disconnected!")
        await ws.send_json({"type": "CLOSED"})

def get_chat_history(chat_id: str):
    return []

def create_new_chat():
    return "chat123"

def save_message(chat_id: str, role: str, content: str):
    print(f"[SAVE] {chat_id} | {role} | {content}")

async def send_model_message(ws, client, messages, chat_id, message_id, user_prompt: str | None = None):

    if user_prompt: 
        messages.append({"role": "user", "content": user_prompt})
        save_message(chat_id, "user", user_prompt)

    while True:
        tool_buffers = {}
        tool_name = {}
        final_response = ""

        stream = client.chat.completions.create(
            model="gemma-3-12b-it-Q4_K_M.gguf",
            messages=messages,
            tools=available_tools,
            stream=True
        )

        for chunk in stream:
            delta = chunk.choices[0].delta

            if delta.tool_calls:
                for t in delta.tool_calls:
                    if t.id:
                        current_id = t.id
                        tool_buffers[current_id] = ""
                        tool_name[current_id] = t.function.name

                    if t.function.arguments:
                        tool_buffers[current_id] += t.function.arguments

            if chunk.choices[0].finish_reason == "tool_calls":
                
                final_tool_calls = []
                for call_id, arg_text in tool_buffers.items():
                    final_tool_calls.append({
                        "id": call_id,
                        "type": "function",
                        "function": {
                            "name": tool_name[call_id],
                            "arguments": arg_text
                        }
                    })

                messages.append({
                    "role": "assistant",
                    "content": "",
                    "tool_calls": final_tool_calls
                })

                tool_names = ", ".join([tool_name[cid] for cid in tool_buffers.keys()])
                await ws.send_json({
                    "type": "UPDATE",
                    "text": f"Executing tools: {tool_names}",
                    "chat_id": chat_id,
                    "message_id": message_id
                })

                tool_outputs = []
                for call_id, arg_text in tool_buffers.items():
                    args = json.loads(arg_text)
                    name = tool_name[call_id]
                    
                    result = run_tool(name, args)
                    
                    tool_outputs.append(f"Function '{name}' returned: {result}")

                combined_output = "\n\n".join(tool_outputs)
                user_followup = f"Here are the tool results:\n{combined_output}\n\nPlease continue and answer my previous question."

                messages.append({
                    "role": "user",
                    "content": user_followup
                })
                
                continue 

            if delta and delta.content:
                token = delta.content
                final_response += token
                await ws.send_json({
                    "type": "MESSAGE",
                    "text": token,
                    "chat_id": chat_id,
                    "message_id": message_id
                })

        if chunk.choices[0].finish_reason != "tool_calls":
            messages.append({
                "role": "assistant",
                "content": final_response
            })
            save_message(chat_id, "assistant", final_response)

            await ws.send_json({
                "type": "UPDATE",
                "text": "complete",
                "chat_id": chat_id,
                "message_id": message_id
            })
            return

def run_tool(name, args):
    print(f"[TOOL] {name} | {args}")
    if name == "web_search":
        return web_search(args["query"], args.get("n", 5))

    if name == "read_webpage":
        return read_webpage(args["url"])

    return "{}"