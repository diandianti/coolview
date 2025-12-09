import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls
import QtQuick.Effects

Window {
    id: mainWindow
    visible: true
    width: 1280
    height: 800
    color: "black"
    title: "Dynamic Grid Viewer"
    
    Shortcut { sequence: "F11";
        onActivated: mainWindow.visibility = (mainWindow.visibility === Window.FullScreen ? Window.Windowed : Window.FullScreen) }
    Shortcut { sequence: "Esc";
        onActivated: if (mainWindow.visibility === Window.FullScreen) mainWindow.visibility = Window.Windowed }

    // --- 全局视觉状态 (绑定到设置面板) ---
    // 布局相关
    property int currentMode: 0
    property int laneCount: 6 // 现在直接代表静态网格的列数
    property real gridSpacing: 0
    property real cellAspectRatio: 1.0 // 新增：单元格长宽比 (Width / Height), 1.0=正方形, >1.0=扁长, <1.0=竖长
    
    // 动画相关
    property real speedFactor: 1.0
    property real breathScale: 1.10
    // 显示相关
    property real globalBrightness: 1.0

    property bool isStreamMode: currentMode >= 3
    property bool isHorizontalStream: currentMode === 5 || currentMode === 6

    // 保存当前的源配置，用于重连
    property var currentSourceConfig: []

    StackLayout {
        id: rootStack
        anchors.fill: parent
        currentIndex: 0 

        // [Index 0] Config Panel (仅数据源)
        ConfigPanel {
            id: configScreen
            onStartRequested: (config) => {
                currentSourceConfig = config.sourceConfig
                // 尝试加载保存的视觉设置
                var saved = Backend.loadSettings()
                if (saved && saved !== "{}") {
                    try {
                        var c = JSON.parse(saved)
                        if (c.visualConfig) applyVisualConfig(c.visualConfig)
                    } catch(e) {}
                }
                
                // 组合完整配置并保存
                saveAllSettings()
                Backend.initSource(JSON.stringify({ "sourceConfig": currentSourceConfig }))
            }
            
            onLoadRequested: {
                var saved = Backend.loadSettings()
                if (saved && saved !== "{}") {
                    try { configScreen.restoreSettings(JSON.parse(saved)) } catch(e) {}
                }
            }
        }

        // [Index 1] Main Viewer
        Item {
            id: mainViewer
            
            // --- Drawer (设置侧边栏) ---
            Drawer {
                id: settingsDrawer
                width: 320
                height: parent.height
                edge: Qt.RightEdge
                modal: false 
                dim: false
                background: Rectangle { 
                    color: "#EE101015"
                    layer.enabled: true
                    layer.effect: MultiEffect { shadowEnabled: true; shadowBlur: 2.0; shadowColor: "black" }
                    Rectangle { width: 1; height: parent.height; color: "#33FFFFFF"; anchors.left: parent.left }
                }

                ColumnLayout {
                    anchors.fill: parent; anchors.margins: 20; spacing: 20
                    
                    Text { text: "VISUAL CONTROL"; color: "white"; font.bold: true; font.pixelSize: 18; font.letterSpacing: 2 }

                    ScrollView {
                        Layout.fillWidth: true; Layout.fillHeight: true
                        clip: true
                        contentWidth: availableWidth
                        
                        ColumnLayout {
                            width: parent.width; spacing: 25

                            // Section 1: Layout
                            SettingsGroup {
                                title: "LAYOUT GRID"
                                SettingsControl {
                                    label: "Mode"
                                    control: ComboBox {
                                        Layout.fillWidth: true
                                        model: ["Classic Mosaic", "Mondrian Style", "Hero Focus", "Waterfall (Vertical)", "Alternate (Vertical)", "Waterfall (Horizontal)", "Alternate (Horizontal)"]
                                        currentIndex: mainWindow.currentMode
                                        onActivated: (idx) => { mainWindow.currentMode = idx; regenerate(); saveAllSettings() }
                                        popup.background: Rectangle { color: "#252525"; border.color: "#444"; radius: 4 }
                                        contentItem: Text { text: parent.displayText; color: "white"; leftPadding: 10; verticalAlignment: Text.AlignVCenter }
                                        background: Rectangle { color: "#22FFFFFF"; radius: 4; border.color: "#44FFFFFF" }
                                    }
                                }
                            
                                SettingsControl {
                                    label: "Columns / Lanes: " + laneCount
                                    control: Slider {
                                        Layout.fillWidth: true; from: 2; to: 24; stepSize: 1; value: mainWindow.laneCount
                                        onMoved: { if (value !== mainWindow.laneCount) { mainWindow.laneCount = value; regenerate() } }
                                        onPressedChanged: if (!pressed) saveAllSettings()
                                    }
                                }
                                SettingsControl {
                                    // 新增：长宽比控制
                                    label: "Cell Aspect Ratio: " + cellAspectRatio.toFixed(2)
                                    control: Slider {
                                        Layout.fillWidth: true; from: 0.5; to: 2.5; stepSize: 0.1; value: mainWindow.cellAspectRatio
                                        onMoved: { if (value !== mainWindow.cellAspectRatio) { mainWindow.cellAspectRatio = value; regenerate() } }
                                        onPressedChanged: if (!pressed) saveAllSettings()
                                        
                                        // 增加刻度指示
                                        ToolTip.visible: pressed
                                        ToolTip.text: value < 0.9 ? "Portrait (竖)" : (value > 1.1 ? "Landscape (横)" : "Square (方)")
                                    }
                                }
                                SettingsControl {
                                    label: "Gap Spacing: " + gridSpacing + "px"
                                    control: Slider {
                                        Layout.fillWidth: true; from: 0; to: 20; stepSize: 1; value: mainWindow.gridSpacing
                                        onMoved: mainWindow.gridSpacing = value
                                        onPressedChanged: if (!pressed) saveAllSettings()
                                    }
                                }
                            }

                            // Section 2: Animation
                            SettingsGroup {
                                title: "ANIMATION"
                                SettingsControl {
                                    label: "Global Speed: " + speedFactor.toFixed(1) + "x"
                                    control: Slider {
                                        Layout.fillWidth: true; from: 0.2; to: 3.0; stepSize: 0.1; value: mainWindow.speedFactor
                                        onMoved: mainWindow.speedFactor = value
                                        onPressedChanged: if (!pressed) saveAllSettings()
                                    }
                                }
                                SettingsControl {
                                    label: "Breath Zoom: " + ((breathScale-1)*100).toFixed(0) + "%"
                                    control: Slider {
                                        Layout.fillWidth: true; from: 1.0; to: 1.3; value: mainWindow.breathScale
                                        onMoved: mainWindow.breathScale = value
                                        onPressedChanged: if (!pressed) saveAllSettings()
                                    }
                                }
                            }

                            // Section 3: Display
                            SettingsGroup {
                                title: "DISPLAY"
                                SettingsControl {
                                    label: "Brightness: " + (globalBrightness*100).toFixed(0) + "%"
                                    control: Slider {
                                        Layout.fillWidth: true; from: 0.2; to: 1.0; value: mainWindow.globalBrightness
                                        onMoved: mainWindow.globalBrightness = value
                                        onPressedChanged: if (!pressed) saveAllSettings()
                                    }
                                }
                            }
                        }
                    }

                    Button {
                        text: "EXIT SESSION"
                        Layout.fillWidth: true; Layout.preferredHeight: 40
                        background: Rectangle { color: "#33AA0000"; radius: 4; border.color: "#AA0000" }
                        contentItem: Text { text: parent.text; color: "#FFAAAA"; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                        onClicked: { rootStack.currentIndex = 0; tileModel.clear(); settingsDrawer.close() }
                    }
                }
            }

            // --- Toolbar ---
            Rectangle {
                id: toolbar
                width: parent.width; height: 60; z: 99
                y: (mouseArea.mouseY < 60 || settingsDrawer.opened) ? 0 : -height
                Behavior on y { NumberAnimation { duration: 300; easing.type: Easing.OutQuart } }
                gradient: Gradient { GradientStop { position: 0.0; color: "#CC000000" } GradientStop { position: 1.0; color: "#00000000" } }

                RowLayout {
                    anchors.fill: parent; anchors.margins: 20
                    Text { text: "✨ VISUAL FLOW"; font.pixelSize: 16; font.bold: true; color: "#FFFFFF"; font.letterSpacing: 2 }
                    Item { Layout.fillWidth: true }
                    Text { text: (Backend ? Backend.totalImages : 0) + " IMG"; color: "#CCC"; font.bold: true; rightPadding: 15 }
                    
                    Button {
                        text: "⚙️ SETTINGS"
                        background: Rectangle { color: "#33FFFFFF"; radius: 15 }
                        contentItem: Text { text: parent.text; color: "white"; font.bold: true; font.pixelSize: 12; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                        onClicked: settingsDrawer.open()
                    }
                }
            }
            
            MouseArea { id: mouseArea; anchors.fill: parent; hoverEnabled: true; z: 0; propagateComposedEvents: true; onPressed: (mouse)=> mouse.accepted = false }

            // --- 内容展示区 ---
            StackLayout {
                anchors.fill: parent
                currentIndex: isStreamMode ? 1 : 0
                
                // 1. Static Grid (网格视图)
                Item {
                    GridLayout {
                        id: mainGrid; anchors.fill: parent
                        rowSpacing: gridSpacing; columnSpacing: gridSpacing
                        columns: 1 // 由 rebuildStaticGrid 动态设置
                        
                        Repeater {
                            model: tileModel
                            delegate: EffectTile {
                                Layout.columnSpan: cSpan; Layout.rowSpan: rSpan; Layout.fillWidth: true; Layout.fillHeight: true
                                isHero: isHeroItem
                                refreshInterval: ((isHeroItem ? 15000 : 5000) / speedFactor) + Math.random() * 2000
                                maxScale: mainWindow.breathScale
                                brightness: mainWindow.globalBrightness
                            }
                        }
                    }
                }
                
                // 2. Stream Grid (瀑布流视图)
                Item {
                    clip: true
                    GridLayout {
                        anchors.fill: parent
                        rowSpacing: gridSpacing; columnSpacing: gridSpacing
                        flow: isHorizontalStream ? GridLayout.TopToBottom : GridLayout.LeftToRight
                        columns: isHorizontalStream ? 1 : Math.max(1, laneCount)
                        rows: isHorizontalStream ? Math.max(1, laneCount) : 1

                        Repeater {
                            id: streamRepeater
                            model: laneCount
                            delegate: ListView {
                                id: flowList
                                Layout.fillHeight: true; Layout.fillWidth: true; clip: true; interactive: false
                                spacing: gridSpacing
                                orientation: isHorizontalStream ? ListView.Horizontal : ListView.Vertical
                                model: 10000 
                                property bool isReverse: (currentMode === 4 || currentMode === 6) ? (index % 2 !== 0) : false
                                property real baseSpeed: 50 * speedFactor
                                property real realSpeed: baseSpeed + (Math.random() * 20)
                                 
                                NumberAnimation {
                                    target: flowList; property: isHorizontalStream ? "contentX" : "contentY"
                                    from: flowList.isReverse ? 50000 : 0; to: flowList.isReverse ? 0 : 50000
                                    duration: (50000 / flowList.realSpeed) * 1000; running: true; loops: Animation.Infinite
                                }
                                
                                delegate: Item {
                                    // 瀑布流模式下也稍微参考一下 AspectRatio，但保持随机性
                                    width: isHorizontalStream ? ListView.view.height * cellAspectRatio * (0.8 + Math.random() * 0.4) : ListView.view.width
                                    height: isHorizontalStream ? ListView.view.height : (ListView.view.width / cellAspectRatio) * (0.8 + Math.random() * 0.4)
                                    
                                    EffectTile { 
                                        anchors.fill: parent
                                        refreshInterval: (10000 / speedFactor) + Math.random() * 5000 
                                        maxScale: mainWindow.breathScale
                                        brightness: mainWindow.globalBrightness
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Connections {
        target: Backend
        function onSessionStarted(success, msg) {
            if (success) { rootStack.currentIndex = 1; regenerate() } 
            else { configScreen.setError(msg) }
        }
    }

    ListModel { id: tileModel }

    // --- 逻辑函数 ---

    function saveAllSettings() {
        var visualConfig = {
            "modeIndex": currentMode,
            "laneCount": laneCount,
            "speedFactor": speedFactor,
            "gridSpacing": gridSpacing,
            "breathScale": breathScale,
            "globalBrightness": globalBrightness,
            "cellAspectRatio": cellAspectRatio
        }
        var fullConfig = {
            "sourceConfig": currentSourceConfig, // 这里是数组
            "visualConfig": visualConfig
        }
        Backend.saveSettings(JSON.stringify(fullConfig))
    }

    function applyVisualConfig(v) {
        if (!v) return
        currentMode = v.modeIndex !== undefined ? v.modeIndex : 0
        laneCount = v.laneCount || 6
        speedFactor = v.speedFactor || 1.0
        gridSpacing = v.gridSpacing !== undefined ? v.gridSpacing : 0
        breathScale = v.breathScale || 1.10
        globalBrightness = v.globalBrightness !== undefined ? v.globalBrightness : 1.0
        cellAspectRatio = v.cellAspectRatio !== undefined ? v.cellAspectRatio : 1.0 // 加载长宽比
    }

    function regenerate() {
        if (!isStreamMode) {
            setupStaticMode()
        } else {
            // [Bug Fix] 切换到瀑布流模式时，强制刷新 Repeater
            // 通过将 model 置为 0 然后重新绑定，强制销毁并重建 ListViews
            // 解决切换视图后图像不立即显示的问题
            streamRepeater.model = 0
            streamRepeater.model = Qt.binding(function() { return laneCount })
        }
    }

    function setupStaticMode() {
        // 1. 直接使用 laneCount 作为列数
        var cols = Math.max(1, laneCount) 
        
        // 2. 根据长宽比计算行数
        // 单元格宽度 = 屏幕宽度 / 列数
        var cellW = mainWindow.width / cols
        // 期望单元格高度 = 宽度 / 长宽比
        var targetCellH = cellW / cellAspectRatio
        // 行数 = 屏幕高度 / 期望高度
        var rows = Math.max(1, Math.round(mainWindow.height / targetCellH))
        
        rebuildStaticGrid(cols, rows, currentMode)
    }

    function rebuildStaticGrid(cols, rows, mode) {
        tileModel.clear()
        mainGrid.columns = cols
        var gridMap = []; for (var i = 0; i < rows; i++) { var r = []; for (var j = 0; j < cols; j++) r.push(false); gridMap.push(r) }
        
        // Hero 模式 (Mode 2)
        if (mode === 2) { 
            var cc = Math.floor(cols/2)-2; var cr = Math.floor(rows/2)-2
            // 确保 Hero 区域在范围内
            if (cc>=0 && cr>=0 && cc+4 <= cols && cr+4 <= rows) { 
                for(var r=cr; r<cr+4; r++) for(var c=cc; c<cc+4; c++) gridMap[r][c]="R"; gridMap[cr][cc]="S" 
            }
        }

        for (var r = 0; r < rows; r++) {
            for (var c = 0; c < cols; c++) {
                if (gridMap[r][c] === true || gridMap[r][c] === "R") continue;
                if (gridMap[r][c] === "S") { tileModel.append({ "cSpan": 4, "rSpan": 4, "isHeroItem": true }); gridMap[r][c]=true; continue }

                var spR = (c<cols-1)&&!gridMap[r][c+1]; var spD = (r<rows-1)&&!gridMap[r+1][c]; var spB = spR&&spD&&!gridMap[r+1][c+1]
                var placed = false
                
                // Mondrian / Mosaic 随机合并
                if (mode === 1 && spB && Math.random()<0.2) { placeTile(2,2,r,c,gridMap); placed=true }
                else if (mode === 0 && spB && Math.random()<0.15) { placeTile(2,2,r,c,gridMap); placed=true }
                
                if (!placed) placeTile(1,1,r,c,gridMap)
            }
        }
    }
    function placeTile(w,h,r,c,map) {
        tileModel.append({ "cSpan": w, "rSpan": h, "isHeroItem": false })
        for(var i=0;i<h;i++) for(var j=0;j<w;j++) map[r+i][c+j]=true
    }

    // --- UI Helper Components ---
    component SettingsGroup: ColumnLayout {
        property string title: ""
        spacing: 10; Layout.fillWidth: true
        Text { text: parent.title; color: "#66FFFFFF"; font.bold: true; font.pixelSize: 11; Layout.topMargin: 10 }
        Rectangle { height: 1; Layout.fillWidth: true; color: "#22FFFFFF" }
    }

    component SettingsControl: ColumnLayout {
        property string label: ""
        property alias control: content.data
        spacing: 5; Layout.fillWidth: true
        Text { text: parent.label; color: "#AAFFFFFF"; font.pixelSize: 12 }
        Item { id: content; Layout.fillWidth: true; Layout.preferredHeight: 30 }
    }
}