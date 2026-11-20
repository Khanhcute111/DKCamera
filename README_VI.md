# DKCaMera

**DKCaMera** là bản tùy biến cá nhân từ dự án mã nguồn mở Photon Camera, định hướng thành ứng dụng camera tiếng Việt với chất ảnh tự nhiên.

- Tên ứng dụng: DKCaMera
- Nhà phát triển hiển thị: DK Studio
- Application ID: `com.dkstudio.dkcamera`
- Thiết bị mục tiêu kiểm tra: Xiaomi Redmi Note 13 Pro 4G
- Android tối thiểu theo mã nguồn hiện tại: Android 11 (API 30)
- Giao diện tiếng Việt: `app/src/main/res/values-vi/strings.xml`

## Định hướng xử lý ảnh

Ưu tiên giữ chi tiết thật, màu da và kết cấu tự nhiên. Không áp dụng làm nét quá mạnh, không tự ý thay đổi khuôn mặt và không ghi đè ảnh gốc.

Mã nguồn nền tảng hiện có các tính năng xử lý nhiều khung hình, giảm nhiễu, HDR+ và siêu phân giải RAISR ở chế độ phù hợp. Một số chức năng AI/model tải về trong ứng dụng có điều kiện tương thích và giấy phép riêng; cần kiểm tra trên thiết bị thực tế trước khi bật mặc định.

## Build APK debug

Yêu cầu JDK 21 và Android SDK/NDK theo cấu hình dự án.

```bash
chmod +x ./gradlew
./gradlew :app:assembleDefaultDebug --no-daemon
```

APK debug dự kiến nằm tại:

```
app/build/outputs/apk/default/debug/
```

Nhánh phát triển hiện tại: `dkcamera-branding`.

## Ghi nhận mã nguồn và giấy phép

DKCaMera được tùy biến từ [Photon Camera](https://github.com/bjzhou/PhotonCamera). Mã nguồn gốc và các thành phần bên thứ ba tiếp tục tuân theo giấy phép tương ứng trong repository; các model, LUT, phông chữ và tài nguyên có thể có điều khoản riêng. Việc đổi tên không làm thay đổi quyền tác giả của mã nguồn nền tảng.

---

**Lưu ý trạng thái:** Việc thêm bản dịch tiếng Việt và cấu hình build tự động đã được đưa lên nhánh phát triển. APK chỉ được xem là hoàn tất sau khi GitHub Actions build thành công và cài đặt/chạy thử trên thiết bị mục tiêu.
