# LORE

A modular, Norse‑themed AI + file home server designed to run fully on your local machine. LORE combines storage, search, media intelligence, and local LLM capabilities into one coherent ecosystem.

---

## Overview

LORE is built around a set of focused components, each named after figures or artifacts from Norse mythology. Every module has a specific job but works together to form a unified local AI stack.

```
Bifrost  → API Gateway & Router
Odin     → Local LLM Engine
Mimir    → Storage System
│
├── Orion      → Media & Photo Intelligence
└── Saraswati  → Metadata Database
Freya    → Frontend Android App
Psyche   → Vector Embedding Database
```

---

## Components

### Freya — Frontend (Jetpack Compose)

Freya is the UI layer of LORE, built using Kotlin and Jetpack Compose.

* Mobile-first interface for browsing files, media, and chatting with Odin.
* Connects directly to Bifrost.
* Provides a polished, modern experience for interacting with the whole system.

### Bifrost — Router & Gateway

Bifrost is the entry point to the entire system. It handles routing, authentication, and acts as the API gateway. All user requests pass through Bifrost before reaching other services.

### Odin — Local LLM Brain

Odin runs one or more local language models.

* Supports a lightweight daily‑chat model and a heavy reasoning model.
* Handles text, file, and image inputs.
* Integrates directly with Mimir for file access.
* Optionally integrates with Psyche for vector search.

### Mimir — File Storage System

Mimir manages all local files with permission‑scoped access.

* Upload, download, rename, delete
* File sharing via permission‑linked tokens
* Embeddings generated from filenames, notes, and content
* Houses two submodules:

#### Orion — Media Intelligence

Handles all media operations, especially photos.

* Uses YOLO/CLIP‑style models for tagging
* Can perform object detection and possible facial recognition
* Sends metadata to Saraswati

#### Saraswati — Metadata Database

A SQLite‑based metadata store.

* Tracks permissions, timestamps, file relationships
* Stores metadata for both Mimir and Orion

### Psyche — Vector Database

Stores and retrieves embeddings for semantic search.

* Implemented with ChromaDB
* Used by Odin and Mimir for similarity search

---

## Tech Stack

* **Backend:** FastAPI
* **DB:** SQLite for metadata, ChromaDB for vector embeddings
* **Models:** Llama 4 (planned), YOLO/CLIP‑style models for images
* **Storage:** Local filesystem

---

## Project Goals

* Fully local AI experience
* Modular components that can be swapped or extended
* Minimal external dependencies
* Smooth file + LLM integration

---

## Setup

*(Placeholder — fill once installation steps are finalized)*

---

## Roadmap

* Automatic model switching
* User roles & granular permissions
* Web UI for browsing Mimir & Orion
* Real‑time media processing
* Cross‑module event system

---

## License

*(Add your chosen license here)*

---

## Contributing

Pull requests and feature ideas are welcome once the public repo is live.

---

## Credits

Designed and built with a blend of engineering chaos and mythological inspiration.
