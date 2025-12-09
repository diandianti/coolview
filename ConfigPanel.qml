import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Effects

Rectangle {
    id: root

    // 向外传递 Source Config (现在是一个数组)
    signal startRequested(var config)
    signal loadRequested

    property color accentColor: "#00E5FF"
    property color secondaryColor: "#BF00FF"
    property color panelColor: "#CC101015"

    // 存储所有添加的源
    ListModel { id: sourceModel }

    function restoreSettings(cfg) {
        if (!cfg || !cfg.sourceConfig) return;
        
        sourceModel.clear()
        var list = cfg.sourceConfig
        // 兼容旧版单一对象配置
        if (!Array.isArray(list)) list = [list]

        for (var i = 0; i < list.length; i++) {
            sourceModel.append(list[i])
        }
    }

    function setError(msg) {
        errorText.text = "⚠️ " + msg;
    }

    // --- 背景动画 ---
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0.0; color: "#0f0c29" }
            GradientStop { position: 0.5; color: "#302b63" }
            GradientStop { position: 1.0; color: "#24243e" }
        }
    }
    Rectangle {
        width: 600; height: 600; radius: 300
        color: Qt.rgba(root.secondaryColor.r, root.secondaryColor.g, root.secondaryColor.b, 0.1)
        x: -100; y: -100
        SequentialAnimation on scale {
            loops: Animation.Infinite
            NumberAnimation { from: 1; to: 1.1; duration: 5000; easing.type: Easing.InOutSine }
            NumberAnimation { from: 1.1; to: 1; duration: 5000; easing.type: Easing.InOutSine }
        }
    }

    // --- 主面板 ---
    Item {
        width: 800 // 加宽以适应左右或上下布局，这里采用垂直布局但内容更多
        height: 700
        anchors.centerIn: parent

        Rectangle {
            anchors.fill: parent
            radius: 20
            color: root.panelColor
            border.color: "#33FFFFFF"; border.width: 1
            layer.enabled: true
            layer.effect: MultiEffect {
                shadowEnabled: true; shadowBlur: 1.5
                shadowColor: "#80000000"; shadowVerticalOffset: 10
            }
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 30
            spacing: 15

            // Header
            Text {
                text: "LIBRARY SOURCES"
                color: "white"
                font.pixelSize: 28; font.bold: true; font.letterSpacing: 4
                Layout.alignment: Qt.AlignHCenter
            }

            // --- Section 1: Source List (已添加的源) ---
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: "#11FFFFFF"
                radius: 8
                clip: true

                ListView {
                    id: sourceListView
                    anchors.fill: parent
                    anchors.margins: 10
                    model: sourceModel
                    spacing: 8
                    
                    delegate: Rectangle {
                        width: sourceListView.width
                        height: 50
                        color: "#22FFFFFF"
                        radius: 6
                        
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 10
                            
                            // 图标
                            Text { 
                                text: model.type === "LOCAL" ? "📂" : (model.type === "SMB" ? "📁" : "🌐")
                                font.pixelSize: 18 
                            }
                            
                            // 描述文本
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text { 
                                    text: model.type
                                    color: root.accentColor
                                    font.bold: true; font.pixelSize: 12
                                }
                                Text {
                                    text: {
                                        if (model.type === "LOCAL") return model.path
                                        if (model.type === "SMB") return model.ip + " / " + model.share
                                        return model.host
                                    }
                                    color: "white"
                                    font.pixelSize: 14
                                    elide: Text.ElideRight
                                    Layout.fillWidth: true
                                }
                            }
                            
                            // 删除按钮
                            Button {
                                text: "Remove"
                                Layout.preferredHeight: 30
                                background: Rectangle {
                                    color: parent.down ? "#44FF0000" : "#22FF0000"
                                    radius: 4
                                    border.color: "#FF4444"
                                }
                                contentItem: Text { text: "✕"; color: "#FF4444"; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                                onClicked: sourceModel.remove(index)
                            }
                        }
                    }
                    
                    // 空列表提示
                    Text {
                        anchors.centerIn: parent
                        text: "No sources added yet.\nUse the form below to add folders or servers."
                        color: "#55FFFFFF"
                        visible: sourceModel.count === 0
                        horizontalAlignment: Text.AlignHCenter
                    }
                }
            }

            // --- Section 2: Editor (添加新源) ---
            Rectangle { 
                Layout.fillWidth: true; height: 1; color: "#22FFFFFF" 
                Layout.topMargin: 10; Layout.bottomMargin: 10
            }

            Text { text: "Add New Source"; color: "#AAAAAA"; font.bold: true }

            TabBar {
                id: sourceTab
                Layout.fillWidth: true
                background: Rectangle { color: "transparent" }
                Repeater {
                    model: ["Local", "SMB", "WebDAV"]
                    TabButton {
                        text: modelData
                        width: implicitWidth
                        contentItem: Text {
                            text: parent.text
                            color: parent.checked ? root.accentColor : "#888"
                            font.bold: parent.checked
                            horizontalAlignment: Text.AlignHCenter
                        }
                        background: Rectangle { 
                            height: 2; width: parent.width; anchors.bottom: parent.bottom
                            color: parent.checked ? root.accentColor : "transparent"
                        }
                    }
                }
            }

            // Editor Fields
            GridLayout {
                columns: 2
                columnSpacing: 15
                rowSpacing: 10
                Layout.fillWidth: true

                // Local Path
                StyledInput { 
                    Layout.columnSpan: 2
                    id: pathField; visible: sourceTab.currentIndex === 0
                    iconText: "📂"; label: "Folder Path"; placeholder: "e.g. C:/Photos" 
                }

                // Remote Common
                StyledInput {
                    Layout.columnSpan: 1
                    id: hostField; visible: sourceTab.currentIndex !== 0
                    iconText: "🌐"; label: sourceTab.currentIndex === 1 ? "Server IP" : "WebDAV URL"
                    placeholder: sourceTab.currentIndex === 1 ? "192.168.1.10" : "https://..."
                }
                StyledInput {
                    Layout.columnSpan: 1
                    id: shareField; visible: sourceTab.currentIndex === 1
                    iconText: "📁"; label: "Share Name"; placeholder: "Media"
                }

                StyledInput {
                    Layout.columnSpan: 1
                    id: userField; visible: sourceTab.currentIndex !== 0
                    iconText: "👤"; label: "Username"; placeholder: "Guest"
                }
                StyledInput {
                    Layout.columnSpan: 1
                    id: passField; visible: sourceTab.currentIndex !== 0
                    iconText: "🔒"; label: "Password"; isPassword: true
                }
                StyledInput {
                    Layout.columnSpan: 2
                    id: subPathField; visible: sourceTab.currentIndex !== 0
                    iconText: "📍"; label: "Subfolder Path"; placeholder: "/"; text: "/"
                }
            }

            // Add Button
            Button {
                text: "+ Add to List"
                Layout.alignment: Qt.AlignRight
                background: Rectangle { color: "#22FFFFFF"; radius: 6; border.color: root.accentColor }
                contentItem: Text { text: parent.text; color: root.accentColor; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; padding: 10 }
                onClicked: addSourceToList()
            }

            Text {
                id: errorText
                text: ""
                color: "#FF4D4D"; font.pixelSize: 12
                Layout.alignment: Qt.AlignHCenter
                visible: text !== ""
            }

            // --- Main Start Button ---
            Button {
                id: startBtn
                Layout.fillWidth: true
                Layout.preferredHeight: 50
                Layout.topMargin: 10
                background: Rectangle {
                    radius: 10
                    gradient: Gradient {
                        orientation: Gradient.Horizontal
                        GradientStop { position: 0.0; color: root.accentColor }
                        GradientStop { position: 1.0; color: root.secondaryColor }
                    }
                    opacity: enabled ? 1.0 : 0.5
                }
                enabled: sourceModel.count > 0

                contentItem: Item {
                    anchors.fill: parent
                    Row {
                        anchors.centerIn: parent; spacing: 10
                        Text { text: "🚀"; font.pixelSize: 18 }
                        Text { text: "START SESSION (" + sourceModel.count + ")"; color: "white"; font.bold: true; font.pixelSize: 16 }
                    }
                }

                onClicked: {
                    errorText.text = "Connecting..."
                    var configList = []
                    for(var i=0; i<sourceModel.count; i++) {
                        configList.push(sourceModel.get(i))
                    }
                    root.startRequested({ "sourceConfig": configList })
                }
            }
        }
    }

    function addSourceToList() {
        var src = {}
        if (sourceTab.currentIndex === 0) {
            if (pathField.text.trim() === "") return
            src = { "type": "LOCAL", "path": pathField.text }
            // Optional: Clear field
            pathField.text = ""
        } else if (sourceTab.currentIndex === 1) {
            if (hostField.text.trim() === "" || shareField.text.trim() === "") return
            src = {
                "type": "SMB", "ip": hostField.text, "share": shareField.text,
                "user": userField.text, "password": passField.text, "path": subPathField.text
            }
        } else {
            if (hostField.text.trim() === "") return
            src = {
                "type": "WEBDAV", "host": hostField.text,
                "user": userField.text, "password": passField.text, "path": subPathField.text
            }
        }
        sourceModel.append(src)
    }

    component StyledInput: ColumnLayout {
        id: inputRoot
        property alias text: field.text
        property alias placeholder: field.placeholderText
        property string label: ""
        property string iconText: ""
        property bool isPassword: false
        spacing: 4
        Layout.fillWidth: true
        Text { text: inputRoot.label; color: "#AAAAAA"; font.pixelSize: 11; font.bold: true }
        TextField {
            id: field
            Layout.fillWidth: true
            Layout.preferredHeight: 36
            echoMode: inputRoot.isPassword ? TextInput.Password : TextInput.Normal
            color: "white"
            font.pixelSize: 13
            leftPadding: 30
            background: Rectangle {
                color: field.activeFocus ? "#22FFFFFF" : "#11FFFFFF"
                radius: 6
                border.color: field.activeFocus ? root.accentColor : "#22FFFFFF"
                border.width: 1
            }
            Text {
                text: inputRoot.iconText
                anchors.left: parent.left; anchors.leftMargin: 8; anchors.verticalCenter: parent.verticalCenter
                font.pixelSize: 12; opacity: 0.7
            }
            placeholderTextColor: "#44FFFFFF"
        }
    }
    Component.onCompleted: root.loadRequested()
}