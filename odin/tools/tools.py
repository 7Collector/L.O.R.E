# Provide a set of tools to the AI Model to further assist it

# 1. File Search
# 2. Vector Search
# 3. Read File
# 4. Photo Description (Pass which was generated when uploaded)
# 5. Metadata
# 6. Web Search - Implemented using Google Custom Search API
# 7. Read Webpage - Implemented using ChatGPT Beautiful Soup
# 8. Run Code (No)

available_tools = [
    {
        "type": "function",
        "function": {
            "name": "file_search",
            "description": "Search for files stored in the user's drive by name or keywords",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search text for filename or metadata"
                    },
                    "limit": {
                        "type": "integer",
                        "description": "Maximum number of results",
                        "default": 10
                    }
                },
                "required": ["query"]
            }
        }
    },

    {
        "type": "function",
        "function": {
            "name": "vector_search",
            "description": "Search embeddings for semantic matches",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search text query"
                    },
                    "top_k": {
                        "type": "integer",
                        "description": "Number of matches to return",
                        "default": 5
                    }
                },
                "required": ["query"]
            }
        }
    },

    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "Read the content of a file from drive by ID",
            "parameters": {
                "type": "object",
                "properties": {
                    "file_id": {
                        "type": "string",
                        "description": "Unique ID of the file"
                    }
                },
                "required": ["file_id"]
            }
        }
    },

    {
        "type": "function",
        "function": {
            "name": "describe_photo",
            "description": "Describe the content of a photo. Pass the photo reference or ID",
            "parameters": {
                "type": "object",
                "properties": {
                    "photo_id": {
                        "type": "string",
                        "description": "ID of the image stored on the server"
                    }
                },
                "required": ["photo_id"]
            }
        }
    },

    {
        "type": "function",
        "function": {
            "name": "get_metadata",
            "description": "Read file metadata such as size, type, tags, timestamps",
            "parameters": {
                "type": "object",
                "properties": {
                    "file_id": {
                        "type": "string",
                        "description": "Unique ID of the file"
                    }
                },
                "required": ["file_id"]
            }
        }
    },

    {
        "type": "function",
        "function": {
            "name": "web_search",
            "description": "Search the web using Google Custom Search API",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search query"
                    },
                    "limit": {
                        "type": "integer",
                        "description": "Number of results",
                        "default": 5
                    }
                },
                "required": ["query"]
            }
        }
    },

    {
        "type": "function",
        "function": {
            "name": "read_webpage",
            "description": "Fetch webpage and extract text with BeautifulSoup",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {
                        "type": "string",
                        "description": "URL of the webpage"
                    }
                },
                "required": ["url"]
            }
        }
    }
]