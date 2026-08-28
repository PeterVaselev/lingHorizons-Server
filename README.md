# 🌐 LING Horizons Server (The Ultimate LOD Streaming Engine)
### Dedicated Server Voxel LOD Streaming & Chunky Integration for Minecraft NeoForge 1.21.1

![LING Horizons Server](logo2.png)

---

## 🌟 ภาพรวมโปรเจกต์ (Overview)

**LING Horizons Server** คือม็อดฝั่ง **Dedicated Server** สำหรับ Minecraft 1.21.1 (NeoForge) ที่ออกแบบมาเพื่อทำงานร่วมกับ **LING Horizons Client** และม็อด **Chunky** เพื่อมอบประสบการณ์การเล่นแบบ Multiplayer ระดับ Next-Gen:

- **🔥 Chunky Real-time Pre-Voxelization**: เมื่อแอดมินใช้คำสั่ง `/chunky start` เซิร์ฟเวอร์จะแปลงชังก์ที่สร้างขึ้นใหม่ทั้งหมดเป็น 3D Voxel DAG และบันทึกลงในฐานข้อมูล Master RocksDB ของ Server ทันที
- **⚡ Asynchronous On-Demand Streaming**: สตรีมข้อมูลภาพ 3D LOD (บีบอัดด้วย ZSTD) ให้กับผู้เล่นที่เชื่อมต่อเข้ามาตามทิศทางสายตา (Frustum Priority)
- **👑 Day-One Full World Visibility**: ผู้เล่นล็อกอินเข้าเซิร์ฟเวอร์ครั้งแรก มองเห็นวิว 3D ภูเขา สิ่งก่อสร้าง และทิวทัศน์ไกลสุด 3,000–5,000 บล็อกทันที โดยไม่ต้องเดินสำรวจเอง
- **🛡️ 20.0 TPS Guaranteed**: ทำงานบน Background Worker Threads แยกต่างหาก ไม่รบกวน Tick Loop หลักของเซิร์ฟเวอร์ และจำกัด Bandwidth อย่างเคร่งครัด (เช่น 50 KB/s ต่อคน)
- **🤝 Vanilla & Multi-Mod Friendly**: ผู้เล่นที่ไม่มี LING Horizons สามารถเข้าเล่นเซิร์ฟเวอร์ได้ตามปกติ 100%

---

## 🚀 วิธีการติดตั้งและใช้งาน (Installation & Setup)

### 1. การติดตั้งบน Dedicated Server:
1. ดาวน์โหลดไฟล์ `lingHorizons-Server-V1-1.0.0.jar`
2. นำไฟล์ไปวางในโฟลเดอร์ `mods/` ของเซิร์ฟเวอร์ NeoForge 1.21.1
3. (แนะนำอย่างยิ่ง) ติดตั้งม็อด **Chunky (NeoForge)** ในโฟลเดอร์ `mods/`

### 2. ขั้นตอนการปั่นแมพด้วย Chunky (The Ultimate Combo):
เข้าเซิร์ฟเวอร์หรือพิมพ์คำสั่งผ่าน Server Console:
```bash
# กำหนดจุดศูนย์กลาง (เช่น จุดเกิดโลก)
/chunky center 0 0

# กำหนดรัศมีที่ต้องการปั่น (เช่น รัศมี 3,000 บล็อก)
/chunky radius 3000

# กำหนดรูปทรงเป็นวงกลมหรือสี่เหลี่ยม
/chunky shape circle

# เริ่มต้นการสร้างโลกและ Voxelize ลงฐานข้อมูล LING Horizons ทันที
/chunky start
```

---

## ⚙️ การปรับแต่งคอนฟิก (`ling_horizons-server.toml`)

ไฟล์คอนฟิกจะถูกสร้างขึ้นอัตโนมัติในโฟลเดอร์ `config/ling_horizons-server.toml`:

```toml
[server_streaming]
    # เปิด/ปิดระบบสตรีม LOD ให้กับผู้เล่น
    streamingEnabled = true

    # รัศมีการส่งข้อมูล LOD สูงสุดให้ผู้เล่นแต่ละคน (หน่วยเป็นชังก์)
    # 64 Chunks  = 1,024 บล็อกรอบตัว
    # 128 Chunks = 2,048 บล็อก (ค่าแนะนำ)
    # 256 Chunks = 4,096 บล็อก (Ultra)
    maxStreamingRadiusChunks = 128

    # จำกัดความเร็วการส่งข้อมูลต่อผู้เล่น 1 คน (หน่วยเป็น KB/s)
    maxBandwidthPerPlayerKB = 50

    # จำนวน Background Worker Threads สำหรับการอ่านและส่งข้อมูล LOD
    streamingWorkerThreads = 2
```

---

## 📊 โครงสร้างการจับคู่ (Client & Server Pair)

| ด้าน | ม็อดที่ใช้งาน | ตำแหน่งติดตั้ง | หน้าที่ |
| :--- | :--- | :--- | :--- |
| **Client** | `lingHorizons-V1-1.0.1.jar` | โฟลเดอร์ `mods/` บนเครื่องผู้เล่น | เรนเดอร์กราฟิก 3D Voxel DAG, Shaders, GPU Compute (144+ FPS) |
| **Server** | `lingHorizons-Server-V1-1.0.0.jar` | โฟลเดอร์ `mods/` บน Dedicated Server | ดักจับ Chunky, บันทึก Master RocksDB, สตรีม Network Payloads |

---

## 📜 License
LGPL-3.0 License.
