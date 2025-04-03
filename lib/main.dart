import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';

void showOverlay() async {
  bool isGranted = await FlutterOverlayWindow.isPermissionGranted();

  if(!isGranted) {
    await FlutterOverlayWindow.requestPermission();
    return;
  }

  await FlutterOverlayWindow.showOverlay(
    height: WindowSize.fullCover,
    width: WindowSize.matchParent,
    alignment: OverlayAlignment.center,
    flag: OverlayFlag.focusPointer,
    overlayTitle: "오버레이 테스트",
    overlayContent: "투명도 설정",
    enableDrag: false,
    visibility: NotificationVisibility.visibilityPublic,
    positionGravity: PositionGravity.none,
    startPosition: null,
  );

  bool isRunning = await FlutterOverlayWindow.isActive();
  print("오버레이: $isRunning");
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FlutterOverlayWindow.showOverlay();
}

// overlay entry point
@pragma("vm:entry-point")
void overlayMain() async {
  runApp(MaterialApp(
    debugShowCheckedModeBanner: false,
    home: CameraScreen(),
  ));
}

class CameraScreen extends StatefulWidget {
  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  late CameraController _controller;
  Future<void>? _initializeControllerFuture;

  @override
  void initState() {
    super.initState();
    _initCamera();
  }

  Future<void> _initCamera() async {
    try {
      final cameras = await availableCameras();
      _controller = CameraController(cameras.first, ResolutionPreset.medium);
      _initializeControllerFuture = _controller.initialize();
      await _initializeControllerFuture;
      if (mounted) {
        setState(() {});
      }
    } catch (e) {
      print("initCam: $e");
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("카메라 화면")),
      backgroundColor: Colors.transparent,
      body: Column(
        children: [
          Expanded(
            child: FutureBuilder<void>(
              future: _initializeControllerFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.done) {
                  return Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Expanded(
                        child: ClipRect(
                          child: Align(
                            alignment: Alignment.centerRight,
                            widthFactor: 1.5,
                            child: CameraPreview(_controller),
                          ),
                        ),
                      ),
                      SizedBox(width: 20),
                      Expanded(
                        child: Align(
                          alignment: Alignment.centerLeft,
                          widthFactor: 1.5,
                          child: CameraPreview(_controller),
                        ),
                      ),
                    ],
                  );
                } else if (snapshot.hasError) {
                  return Center(child: Text("에러문: ${snapshot.error}"));
                } else {
                  return const Center(child: CircularProgressIndicator());
                }
              },
            ),
          ),
        ],
      ),
    );
  }
}