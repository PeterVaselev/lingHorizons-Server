# 🌐 สถาปัตยกรรมระบบ Server-Side LOD Streaming & Chunky Integration
## LING Horizons — Next-Gen Multiplayer Voxel LOD Engine (NeoForge 1.21.1)

---

## 1. บทนำและวิสัยทัศน์ (Executive Summary & System Vision)

ในสภาพแวดล้อม **Multiplayer (Dedicated Server)** ของ Minecraft เซิร์ฟเวอร์มักถูกจำกัด `view-distance` ไว้ที่ 8–12 ชังก์ (~128–192 เมตร) เพื่อรักษาค่า **20.0 TPS** และควบคุม Bandwidth ทำให้ผู้เล่นไม่สามารถมองเห็นทิวทัศน์ระยะไกลได้ และเกิดปัญหาเกาะสี่เหลี่ยมตัดขาดลอยกลางความว่างเปล่า (Void Cutoff)

**LING Horizons Server-Side LOD Streaming** ได้รับการออกแบบขึ้นเพื่อเป็น **"The Ultimate Server Combo"** ร่วมกับม็อด **Chunky**:
1. **เซิร์ฟเวอร์ปั่นแมพครั้งเดียวด้วย Chunky**: ข้อมูลทั้งโลก (เช่น รัศมี 5,000–10,000 บล็อก) จะถูกแปลงเป็น 3D Voxel DAG และบันทึกลงฐานข้อมูล Master RocksDB บนเซิร์ฟเวอร์แบบ Real-time
2. **สตรีมข้อมูลภาพ 3D LOD ผ่าน Network Payload แบบ On-Demand**: เมื่อผู้เล่นล็อกอินเข้าเซิร์ฟเวอร์ ข้อมูล LOD ระยะไกล (ที่ถูกบีบอัดด้วย ZSTD ระดับสูง) จะถูกส่งตรงเข้าเครื่องผู้เล่นทันที
3. **ผู้เล่นมองเห็นโลกทั้งใบ 3,000+ บล็อกตั้งแต่วินาทีแรก**: โดยที่เซิร์ฟเวอร์ยังคงรันที่ 20.0 TPS นิ่งสนิท และกินปริมาณอินเทอร์เน็ตเพียงไม่กี่กิโลไบต์ต่อวินาที

---

## 2. แผนผังสถาปัตยกรรมระบบ (System Architecture & Data Flow)

```
[ DEDICATED SERVER ]
 ├── 1. Chunky Pre-Generator (/chunky start)
 │       └── Hook ผ่าน MixinNeoForgeWorld ──> Ingest Chunks เข้า Voxel Pipeline
 ├── 2. Master Voxel Storage Engine
 │       └── บันทึก 3D Voxel DAG (Level 0 - 4) ลง RocksDB กลางของ Server (world/ling_horizons/)
 └── 3. LOD Streaming Network Server
         ├── ติดตามพิกัดและทิศทางการมองของผู้เล่นแต่ละคน (Player Frustum Tracker)
         ├── จัดคิวส่งข้อมูลตามลำดับความสำคัญ (Distance & Priority Queue)
         └── บีบอัดข้อมูลด้วย ZSTD ──> ส่งผ่าน NeoForge CustomPacketPayload
                                       │
                    [ NeoForge Network Channel: ling_horizons:lod_stream ]
                                       │
[ PLAYER CLIENT ]                      ▼
 ├── 4. LOD Network Client Receiver
 │       └── รับก้อนข้อมูลบีบอัด ──> คลายการบีบอัดบน Background Worker Thread
 └── 5. GPU Rendering Engine (LING Horizons Client)
         └── อัปโหลดเข้าสู่ GPU SSBO / Hierarchical Octree ──> เรนเดอร์วิวระยะไกล 144+ FPS ทันที
```

---

## 3. ข้อกำหนดโพรโทคอลเน็ตเวิร์ก (Network Protocol Specification)

บน **NeoForge 1.21.1** ระบบจะลงทะเบียน Custom Payloads ผ่าน `RegisterPayloadHandlersEvent` ของ Mod Event Bus ดังนี้:

### 3.1 Payloads Definition

| Packet Name | Direction | วัตถุประสงค์ | โครงสร้างข้อมูล (Payload Structure) |
| :--- | :---: | :--- | :--- |
| **`LingHelloPayload`** | `S -> C` / `C -> S` | แฮนด์เชกเริ่มต้นเพื่อยืนยันว่ามีม็อดทั้งสองฝั่ง | `int protocolVersion`, `int maxStreamingRadius`, `boolean serverCompression` |
| **`LingLodRequestPayload`** | `C -> S` | Client ร้องขอก้อนข้อมูล LOD ในพื้นที่ที่กล้องมอง | `int centerChunkX`, `int centerChunkZ`, `int radius`, `int requestedLodLevel` |
| **`LingLodDataPayload`** | `S -> C` | Server ส่งก้อนข้อมูล 3D Voxel Section ที่บีบอัดแล้ว | `int sectionX`, `int sectionY`, `int sectionZ`, `int lodLevel`, `byte[] zstdData` |
| **`LingWorldUpdatePayload`** | `S -> C` | บรอดแคสต์อัปเดตเมื่อมีผู้เล่นทุบ/วางบล็อกในโลก | `int chunkX`, `int chunkZ`, `long blockChangeMask` |

---

## 4. กลยุทธ์การควบคุมแบนด์วิดท์และป้องกันเซิร์ฟเวอร์หน่วง (Anti-Lag & Bandwidth Control)

1. **ลำดับความสำคัญตามมุมมองกล้อง (Frustum & Distance Priority Queue)**:
   - เซิร์ฟเวอร์จะส่งชังก์ที่อยู่ **ตรงหน้าสายตาผู้เล่นก่อน** ชังก์ที่อยู่ด้านหลัง
   - ส่งชังก์ความละเอียดหยาบ (LOD Level 2-4) ก่อน เพื่อให้เห็นโครงร่างภูเขาทันที แล้วค่อยส่งรายละเอียดระดับสูง (LOD Level 0-1) ตามหลัง
2. **จำกัดความเร็วการส่งข้อมูล (Adaptive Bandwidth Throttling)**:
   - จำกัดขนาดการส่งข้อมูลสูงสุดไม่เกิน **30–50 KB/s ต่อผู้เล่น**
   - หากผู้เล่นมีค่า Ping สูง ระบบจะลดอัตราการส่งลงอัตโนมัติ ไม่รบกวนแพ็กเก็ตการต่อสู้หรือการเคลื่อนที่ของตัวเกม
3. **การแคชข้อมูลซ้ำซ้อนบนเครื่อง Client (Client-Side Incremental Cache)**:
   - เมื่อ Client ได้รับก้อน LOD มาแล้ว จะบันทึกลงในไดเรกทอรี `.voxy/saves/<server_ip>/` บนเครื่องของตัวเองทันที
   - ในการเข้าเล่นครั้งต่อไป Client จะขอเฉพาะชังก์ที่มีการเปลี่ยนแปลง (Delta Update) เท่านั้น

---

## 5. แผนขั้นตอนการพัฒนา (Implementation Roadmap)

### เฟสที่ 1: ระบบเครือข่ายพื้นฐาน (Networking Core & Handshake)
- [ ] สร้างคลาส `LingNetworking.java` จัดการลงทะเบียนผ่าน NeoForge `RegisterPayloadHandlersEvent`
- [ ] สร้างคลาส Payloads: `LingHelloPayload`, `LingLodRequestPayload`, `LingLodDataPayload`
- [ ] ทดสอบ Handshake ระหว่าง Dedicated Server และ Client

### เฟสที่ 2: เอนจินฝั่งเซิร์ฟเวอร์ (Server-Side Master Engine & Chunky Link)
- [ ] สร้าง `LingServerInstance.java` สำหรับจัดการเปิดฐานข้อมูล RocksDB บน Server (`world/ling_horizons/`)
- [ ] เชื่อมต่อ `MixinNeoForgeWorld` ให้เซฟชังก์ที่ Chunky ปั่นลงฐานข้อมูลของเซิร์ฟเวอร์โดยตรง
- [ ] สร้าง `ServerLodStreamingService.java` ทำหน้าที่ดึงข้อมูลจาก RocksDB มาแพ็กเป็น Packet ส่งให้ผู้เล่น

### เฟสที่ 3: ระบบรับและอัปเดตข้อมูลฝั่งไคลเอนต์ (Client Network Ingest & Cache)
- [ ] ปรับแต่ง `LingClientInstance.java` ให้รองรับการรับ Voxel Data จาก Server Stream เข้าสู่ Renderer
- [ ] รองรับการแคชข้อมูลลงดิสก์ฝั่ง Client เพื่อลดภาระการขอข้อมูลซ้ำในอนาคต

### เฟสที่ 4: ระบบซิงก์การเปลี่ยนแปลงโลกแบบ Real-time (Live World Delta Sync)
- [ ] ดักจับ Block Change Events บนเซิร์ฟเวอร์ เมื่อมีผู้เล่นสร้าง/ทำลายบล็อกขนาดใหญ่ และส่ง Packet อัปเดตให้ Client บริเวณใกล้เคียง

---

## 6. ความเข้ากันได้และความปลอดภัย (Compatibility & Fallbacks)

- **ผู้เล่นที่ไม่มีม็อด LING Horizons**: สามารถเข้าเล่นเซิร์ฟเวอร์ได้ตามปกติ 100% โดยเซิร์ฟเวอร์จะไม่ส่งแพ็กเก็ต LOD ไปรบกวน
- **ผู้เล่นที่มีม็อด LING Horizons แต่เล่นบนเซิร์ฟเวอร์ที่ไม่มีม็อด**: ไคลเอนต์จะสลับไปใช้ระบบ Local Caching ดั้งเดิมโดยอัตโนมัติ ไร้ปัญหา Crash
- **การทำงานร่วมกับ Mod ป้องกันพื้นที่ (WorldGuard, FTB Chunks, Claim Mods)**: ระบบ LOD ดึงเฉพาะข้อมูลบล็อกภูมิประเทศ ไม่ก้าวก่ายสิทธิ์ของ Claim Plugins ใดๆ

---

*เอกสารฉบับนี้ถูกสร้างขึ้นเพื่อเป็นพิมพ์เขียว (Blueprint) ในการพัฒนาโมดูล Server LOD Streaming สำหรับ LING Horizons บน NeoForge 1.21.1*
