import QtQuick
import QtQuick.Controls

Item {
    id: root

    property int refreshInterval: 5000
    property int animDuration: 1000
    property bool isHero: false 

    // 新增：接收外部控制的参数
    property real maxScale: 1.15 // 呼吸缩放的最大倍数
    property real brightness: 1.0 // 亮度 (1.0 = 原图, <1.0 = 变暗)

    property string currentSource: ""
    property string nextSource: "" 
    clip: true 

    Item {
        id: backLayer
        anchors.fill: parent; y: 0 
        Image {
            id: backImg
            anchors.fill: parent
            source: root.nextSource
            fillMode: Image.PreserveAspectCrop
            asynchronous: true 
            cache: true 
            sourceSize.width: root.width * (root.isHero ? 1.2 : 1.5)
            sourceSize.height: root.height * (root.isHero ? 1.2 : 1.5)
            visible: status === Image.Ready
            opacity: root.brightness // 绑定亮度
        }
    }

    Item {
        id: frontLayer
        anchors.fill: parent
        transform: Rotation {
            id: frontRotation
            origin.x: frontLayer.width/2
            origin.y: frontLayer.height/2
            axis { x: 1; y: 0; z: 0 }
            angle: 0
        }
        Image {
            id: frontImg
            anchors.fill: parent
            source: root.currentSource
            fillMode: Image.PreserveAspectCrop
            asynchronous: true
            cache: true
            sourceSize.width: root.width * (root.isHero ? 1.2 : 1.5)
            sourceSize.height: root.height * (root.isHero ? 1.2 : 1.5)
            opacity: root.brightness // 绑定亮度
        }
        Rectangle { id: dimmer; anchors.fill: parent; color: "black"; opacity: 0 }
    }

    // --- 呼吸动画 (动态绑定 maxScale) ---
    SequentialAnimation {
        id: zoomAnim 
        running: true
        loops: Animation.Infinite
        
        NumberAnimation {
            target: frontImg
            property: "scale"
            from: 1.0
            to: root.maxScale // 使用动态参数
            duration: 15000
            easing.type: Easing.InOutSine
        }
        
        NumberAnimation {
            target: frontImg
            property: "scale"
            from: root.maxScale // 使用动态参数
            to: 1.0
            duration: 15000
            easing.type: Easing.InOutSine
        }
    }

    function onAnimFinished() {
        root.currentSource = root.nextSource
        resetStates()
        zoomAnim.restart()
        loadNext()
        cycleTimer.restart()
    }
    
    function resetStates() {
        frontLayer.opacity = 1
        frontLayer.y = 0
        frontLayer.x = 0 
        frontRotation.angle = 0
        backLayer.y = 0
        backLayer.x = 0
        frontImg.scale = 1.0
    }

    // 1. 淡入淡出
    NumberAnimation { id: animFade; target: frontLayer; property: "opacity"; to: 0; duration: root.animDuration; easing.type: Easing.InOutQuad; onFinished: root.onAnimFinished() }
    
    // 2. 垂直滑动
    ParallelAnimation {
        id: animSlide
        onFinished: root.onAnimFinished()
        NumberAnimation { target: frontLayer; property: "y"; to: -root.height; duration: root.animDuration; easing.type: Easing.OutExpo }
        NumberAnimation { target: backLayer; property: "y"; from: root.height * 0.5; to: 0; duration: root.animDuration; easing.type: Easing.OutExpo }
    }

    // 3. 翻转
    NumberAnimation { id: animFlip; target: frontRotation; property: "angle"; to: -90; duration: root.animDuration; easing.type: Easing.InOutQuad; onFinished: root.onAnimFinished() }

    // 4. 斜向进入
    ParallelAnimation {
        id: animDiagonal
        onFinished: root.onAnimFinished()
        NumberAnimation { target: frontLayer; property: "x"; to: -root.width; duration: root.animDuration; easing.type: Easing.InOutQuart }
        NumberAnimation { target: frontLayer; property: "y"; to: -root.height; duration: root.animDuration; easing.type: Easing.InOutQuart }
        NumberAnimation { target: backLayer; property: "x"; from: root.width; to: 0; duration: root.animDuration; easing.type: Easing.InOutQuart }
        NumberAnimation { target: backLayer; property: "y"; from: root.height; to: 0; duration: root.animDuration; easing.type: Easing.InOutQuart }
    }

    Timer {
        id: initTimer
        interval: 100; running: true; repeat: true
        onTriggered: {
            var path = Backend.getNextImage()
            if (path !== "") {
                root.currentSource = path;
                running = false; loadNext(); cycleTimer.start()
            }
        }
    }
    Timer { id: cycleTimer; interval: root.refreshInterval; running: false; repeat: false; onTriggered: attemptTransition() }

    function loadNext() {
        var path = Backend.getNextImage()
        if (path !== "") root.nextSource = path
    }

    function attemptTransition() {
        if (root.nextSource !== "" && backImg.status === Image.Ready) {
            if (root.isHero) { animFade.start(); return }

            var dice = Math.random()
            if (dice < 0.25) animFade.start()
            else if (dice < 0.50) { backLayer.y = root.height * 0.5; backLayer.x = 0; animSlide.start() }
            else if (dice < 0.75) animFlip.start()
            else { animDiagonal.start() }
        } else {
            cycleTimer.interval = 500; cycleTimer.restart()
        }
    }
}