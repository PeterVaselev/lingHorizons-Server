# LING Horizons Server (The Ultimate LOD Streaming Engine)
### Dedicated Server Voxel LOD Streaming & Intelligent Chunky Auto-Bridge for NeoForge 1.21.1

![LING Horizons Server](logo2.png)

---

## Project Overview

**LING Horizons Server** is a **Dedicated Server** mod for Minecraft 1.21.1 (NeoForge). It operates as a centralized **Master Voxel DAG Engine**, persistently indexing, caching, and streaming high-resolution Level-of-Detail (LOD) terrain data to connected players running the **LING Horizons Client**:

- **Smart Auto-Detect & Resume**:
  - Automatically scans for unfinished Chunky pre-generation tasks on disk (`config/chunky/tasks/`). If an existing task is detected, it automatically issues `/chunky continue` to seamlessly resume generation without blocking the console for manual confirmation.
- **Auto-Start Chunky on Boot**:
  - If no prior task exists, the server automatically executes preconfigured Chunky commands (e.g., shape, center, radius, start) after a configurable startup delay.
- **Built-in Chunky Auto-Bridge**:
  - **Active Players (>= 1 online)**: Automatically triggers `chunky pause` to yield 100% of CPU/RAM to players, maintaining a rock-solid **20.00 TPS**.
  - **Empty Server (0 online)**: Automatically triggers `chunky resume` to utilize server idle time for continuous chunk pre-generation and Voxel LOD baking.
- **Hierarchical Radial Streaming (Instant Horizon, Zero Void)**:
  - Immediately transmits coarse horizon data (**Coarse LOD 4 & 3**, ~15–30 KB) upon join/teleport to eliminate see-through void and empty gaps.
  - Progressively streams high-fidelity detail (**Fine LOD 2, 1, 0**) sorted radially from the player's position outwards.
- **Adaptive Token-Bucket Bandwidth Limiter**:
  - Regulates packet throughput per player (default 50 KB/s) to prevent network congestion, ensuring **stable player ping (<30ms)** even during high-speed exploration.
- **Fast Non-Blocking Graceful Shutdown**:
  - Shuts down in **~0.1 seconds** on `/stop` while safely and cleanly flushing RocksDB database caches to disk.
- **Vanilla & Multi-Mod Friendly**:
  - Players without the LING Horizons Client can still join and play normally without being forced to install the mod.

---

## ⚙️ Configuration (`config/ling_horizons-server.toml`)

The configuration file is automatically generated at `config/ling_horizons-server.toml`:

```toml
# ==============================================================================
# LING Horizons Server Configuration
# ==============================================================================

# Enable or disable the LOD streaming subsystem for connected players
streamingEnabled = true

# Maximum LOD streaming radius per player (in chunks)
# 64 Chunks  = 1,024 blocks radius
# 128 Chunks = 2,048 blocks (Recommended for standard servers)
# 256 Chunks = 4,096 blocks (For high-spec dedicated servers)
maxStreamingRadiusChunks = 128

# Bandwidth throttle limit per player (in KB/s) to prevent network lag/ping spikes
maxBandwidthPerPlayerKB = 50

# Number of background worker threads dedicated to LOD streaming during active gameplay
streamingWorkerThreads = 2

# Enable dynamic workload scheduling based on player activity
adaptiveWorkloadEnabled = true

# Number of worker threads allocated for accelerated voxel generation when server is idle (0 players)
offlineBoostThreads = 4

# Enable or disable automatic Chunky management (pause on player join, resume on empty)
autoManageChunky = true

# Suppress Chunky Auto-Bridge status notices in the console
silentChunkyBridgeLogs = false

# ------------------------------------------------------------------------------
# Auto-Start Chunky on Boot
# ------------------------------------------------------------------------------

# Enable or disable running Chunky command sequences automatically after server boots
autoStartChunkyOnBoot = true

# Auto-detect saved tasks on disk and execute '/chunky continue' (bypassing confirmation prompts)
autoDetectExistingTask = true

# Delay in seconds after server startup before executing commands (allows other mods to finish loading)
autoStartDelaySeconds = 10

# List of Chunky commands to execute sequentially on boot (when no existing task is pending)
autoStartCommands = [
    "chunky shape circle",
    "chunky center 0 0",
    "chunky radius 3000",
    "chunky start"
]
```

---

## Adaptive Workload Modes

| Server State | Operating Mode | Resource Allocation | Chunky Behavior |
| :--- | :--- | :--- | :--- |
| **Players Online (>= 1)** |  `ACTIVE GAMEPLAY MODE` | 2 Threads (Prioritizes streaming & strict bandwidth throttle) |  **Paused** (100% pause for stable 20.00 TPS) |
| **Server Empty (0)** |  `OFFLINE BOOST MODE` | 4 Threads (4x faster Voxel Ingestion & Baking) | ▶ **Resumed** (Continuously pre-generates chunks & LODs) |

---

## 🧩 Architecture: Client & Server Pair

| Side | Mod Archive | Installation Target | Role / Responsibilities |
| :--- | :--- | :--- | :--- |
|  **Client** | `lingHorizons-V1-1.0.1.jar` | Player's `mods/` directory | Renders 3D Voxel DAGs via Compute Shaders, rendering horizons up to maximum view distances |
|  **Server** | `lingHorizons-Server-V1-1.0.0.jar` | Dedicated Server `mods/` directory | Chunky auto-bridge, Master RocksDB persistence, hierarchical LOD packet compression & streaming |

---



