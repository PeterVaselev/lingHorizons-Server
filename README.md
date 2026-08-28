# 🌐 LING Horizons Server (The Ultimate LOD Streaming Engine)
### ⚡ Dedicated Server Voxel LOD Streaming & Intelligent Chunky Auto-Bridge for NeoForge 1.21.1

![LING Horizons Server](logo2.png)

---

## 🌟 ภาพรวมโปรเจกต์ (Overview)

**LING Horizons Server** คือม็อดฝั่ง **Dedicated Server** สำหรับ Minecraft 1.21.1 (NeoForge) ที่ทำหน้าที่เป็นศูนย์กลางประมวลผล **Master Voxel DAG Engine** บันทึกและสตรีมข้อมูลภูมิประเทศความละเอียดสูง (LOD) สู่ผู้เล่นที่ติดตั้ง **LING Horizons Client**:

- **🚀 Auto-Start Chunky on Boot (สั่งเปิดปั่น Chunk อัตโนมัติเมื่อเปิดเซิร์ฟ)**:
  - สามารถสั่งให้เซิร์ฟเวอร์รันชุดคำสั่งของ Chunky ที่กำหนดไว้ในคอนฟิกโดยอัตโนมัติทันทีที่เปิดเซิร์ฟเวอร์เสร็จสิ้น (ทำงานต่อทันทีโดยแอดมินไม่ต้องพิมพ์คำสั่งเอง)
- **🤖 Built-in Chunky Auto-Bridge (ระบบบริหาร Chunky อัจฉริยะ)**:
  - **เมื่อมีผู้เล่นเข้าเกม (>= 1 คน)**: สั่ง `chunky pause` อัตโนมัติทันที 100% เพื่อคืน CPU / RAM ให้ผู้เล่นเล่นได้ลื่นไหลที่สุด (TPS 20.00 นิ่งสนิท)
  - **เมื่อเซิร์ฟเวอร์ว่าง (0 คน)**: สั่ง `chunky resume` อัตโนมัติทันที เพื่อใช้เวลาว่างสร้างโลกและทำ Voxel LOD ต่อเนื่อง
- **🌅 Hierarchical Radial Streaming (เห็นขอบฟ้าทันที ไร้รอยโหว่)**:
  - ส่งภาพรวมขอบฟ้าหยาบ (**Coarse LOD 4 & 3**) ก่อนทันที (~15–30 KB) ป้องกันปัญหาช่องว่างโปร่งใสหรือมองทะลุโลก (No See-through Void)
  - ส่งรายละเอียดใกล้ตัว (**Fine LOD 2, 1, 0**) เรียงตามระยะวงกลมจากใกล้ตัวผู้เล่นที่สุดออกไปรอบนอก
- **🛡️ Adaptive Token-Bucket Bandwidth Limiter**:
  - จำกัดอัตราการส่งข้อมูลไม่ให้เกินค่าคอนฟิก (ค่าเริ่มต้น 50 KB/s ต่อผู้เล่น) ทำให้ **Ping ของผู้เล่นนิ่งสนิท (<30ms)** แม้จะบินสำรวจด้วยความเร็วสูง
- **⚡ Fast Non-Blocking Graceful Shutdown**:
  - ปิดตัวลงได้อย่างรวดเร็วใน **0.1 วินาที** เมื่อสั่ง `/stop` พร้อมบันทึก Flush ฐานข้อมูล RocksDB ลง SSD อย่างปลอดภัย 100%
- **🤝 Vanilla & Multi-Mod Friendly**:
  - ผู้เล่นที่ไม่มี LING Horizons สามารถเข้าเล่นเซิร์ฟเวอร์ได้ตามปกติ ไม่บังคับติดตั้ง

---

## ⚙️ การตั้งค่าคอนฟิก (`config/ling_horizons-server.toml`)

ไฟล์คอนฟิกจะถูกสร้างขึ้นอัตโนมัติใน `config/ling_horizons-server.toml`:

```toml
# ==============================================================================
# LING Horizons Server Configuration
# ==============================================================================

# เปิด/ปิดระบบสตรีม LOD ให้กับผู้เล่นที่เชื่อมต่อเข้ามา
streamingEnabled = true

# รัศมีการส่งข้อมูล LOD สูงสุดให้ผู้เล่นแต่ละคน (หน่วยเป็นชังก์)
# 64 Chunks  = 1,024 บล็อกรอบตัว
# 128 Chunks = 2,048 บล็อก (ค่าแนะนำสำหรับเซิร์ฟเวอร์ทั่วไป)
# 256 Chunks = 4,096 บล็อก (สำหรับเซิร์ฟเวอร์สเปกสูง)
maxStreamingRadiusChunks = 128

# จำกัดความเร็วการส่งข้อมูลต่อผู้เล่น 1 คน (หน่วยเป็น KB/s) ป้องกัน Ping พุ่ง
maxBandwidthPerPlayerKB = 50

# จำนวน Background Worker Threads สำหรับการสตรีม LOD ตอนมีผู้เล่นออนไลน์
streamingWorkerThreads = 2

# สลับโหมดอัตโนมัติ (Dynamic Workload Scheduler) ตามสถานะผู้เล่น
adaptiveWorkloadEnabled = true

# จำนวน Worker Threads สำหรับเร่งเจน Voxel ตอนเซิร์ฟเวอร์ว่าง (0 คน)
offlineBoostThreads = 4

# เปิด/ปิดระบบหยุด Chunky อัตโนมัติเมื่อมีคนเล่น และเริ่มต่อเมื่อคนออกหมด
autoManageChunky = true

# ซ่อนข้อความแจ้งเตือนสถานะ Chunky Auto-Bridge ใน Console
silentChunkyBridgeLogs = false

# ------------------------------------------------------------------------------
# 🚀 ระบบเปิด Chunky อัตโนมัติตอนเปิดเซิร์ฟเวอร์ (Auto-Start Chunky on Boot)
# ------------------------------------------------------------------------------

# เปิด/ปิดการรันชุดคำสั่ง Chunky อัตโนมัติเมื่อเซิร์ฟเวอร์บูตเสร็จสิ้น
autoStartChunkyOnBoot = true

# ระยะเวลารอ (วินาที) หลังเซิร์ฟเวอร์เปิดเสร็จ ก่อนเริ่มรันคำสั่ง (ให้ม็อดอื่นโหลดเสร็จสมบูรณ์)
autoStartDelaySeconds = 5

# รายการคำสั่งของ Chunky ที่ต้องการให้เซิร์ฟเวอร์รันเรียงตามลำดับอัตโนมัติ
# สามารถปรับแต่งรูปทรง (shape), จุดศูนย์กลาง (center), รัศมี (radius), หรือใช้ chunky continue ได้ตามต้องการ
autoStartCommands = [
    "chunky shape circle",
    "chunky center 0 0",
    "chunky radius 3000",
    "chunky start"
]
```

---

## 📊 ตารางเปรียบเทียบโหมดการทำงาน (Adaptive Workload)

| สถานะเซิร์ฟเวอร์ | โหมดการทำงาน | การจัดสรรทรัพยากร | พฤติกรรมของ Chunky |
| :--- | :--- | :--- | :--- |
| **มีผู้เล่นออนไลน์ (>= 1 คน)** | 🎮 `ACTIVE GAMEPLAY MODE` | 2 Threads (เน้นสตรีม LOD & คุม Bandwidth) | 🛑 **Paused** (หยุด 100% เพื่อ TPS 20.00) |
| **เซิร์ฟเวอร์ว่าง (0 คน)** | 🚀 `OFFLINE BOOST MODE` | 4 Threads (เร่งความเร็ว Voxel Ingest 4x) | ▶️ **Resumed** (ปั๊มแมพและทำ LOD ต่อเนื่อง) |

---

## 🧩 โครงสร้างการจับคู่ม็อด (Client & Server Pair)

| ฝั่ง | ม็อดที่ใช้งาน | ตำแหน่งติดตั้ง | หน้าที่ |
| :--- | :--- | :--- | :--- |
| 💻 **Client** | `lingHorizons-V1-1.0.1.jar` | โฟลเดอร์ `mods/` ของผู้เล่น | เรนเดอร์ 3D Voxel DAG, Compute Shaders, แสดงผลขอบฟ้าไกลสุดสายตา |
| 🖥️ **Server** | `lingHorizons-Server-V1-1.0.0.jar` | โฟลเดอร์ `mods/` บน Dedicated Server | คุม Chunky อัตโนมัติ, บันทึก Master RocksDB, สตรีม Hierarchical LOD Packets |

---

## 📜 License
LGPL-3.0 License.
