# 🖥️ Remotely 🎮

![License](https://img.shields.io/github/license/redxax/Remotely)
![Version](https://img.shields.io/github/v/tag/redxax/Remotely?label=version)
![Java](https://img.shields.io/badge/java-21-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21-blue.svg)

**Remotely** is a powerful Minecraft client mod that integrates a fully-functional terminal directly into your game. Whether you're managing servers, executing scripts, or simply need a command-line interface while gaming, Remotely Terminal has you covered.

---

## 🛠️ Features

### 🎛️ Multi-Terminal Support
- **Multiple Tabs:** Open and manage multiple terminal instances simultaneously.
- **Tab Management:** Easily add, rename, and close terminal tabs with intuitive controls.

### 🔒 SSH Integration
- **Secure Connections:** Connect to remote servers securely using SSH.
- **Remote Command Execution:** Execute commands on remote servers with full support for tab completion.

### ⌨️ Advanced Input Handling
- **Tab Completion:** Intelligent tab completion for commands, directories, and executables.
- **Keyboard Shortcuts:** Customize and use shortcuts for frequently used commands and snippets.
- **History Navigation:** Navigate through your command history with ease using arrow keys.

### 📜 Command Snippets
- **Create & Manage Snippets:** Save and organize frequently used command sequences as snippets.
- **Shortcut Assignments:** Assign shortcuts to snippets for quick execution.
- **Snippet Panel:** Access and manage your snippets from a dedicated panel within the GUI.

### 📂 Session Management
- **Save & Load Sessions:** Persist your terminal outputs and sessions across game restarts.
- **Log Management:** Automatically save terminal logs for future reference and debugging.

### 🎨 Customizable Interface
- **Resizable Panels:** Adjust the size of terminal and snippet panels to fit your preferences.
- **Theming Support:** (Soon) Customize colors and themes to match your Minecraft aesthetic.

---

## 🔧 Requirements
- **Minecraft:** 1.21+ And Above.
- **Fabric Loader:** 0.16.3 And Above.
- **Java:** Java 21 or higher.
- **Fabric API:** 0.102.0 And Above.

---

## 📝 Usage

### 🔑 Opening the Terminal
- **Default Keybinding:** Press `Z` to toggle the Remotely Terminal GUI.

### 📂 Managing Tabs
- **Add Tab:** Click the `+` button in the terminal tab bar.
- **Rename Tab:** Right-click on a tab and select rename or use the rename shortcut.
- **Close Tab:** Middle-click or right-click and select close.

### 🔐 Connecting via SSH
1. **Initiate SSH Connection:**
   - Type `ssh user@host` in the terminal and press `Enter`.
   
2. **Enter Password:**
   - When prompted, enter your SSH password. Input is masked for security.
   
3. **Execute Remote Commands:**
   - Once connected, execute commands as you would in a standard SSH session.

### 📜 Using Snippets
1. **Create Snippet:**
   - Open the snippets panel and click on `Create Snippet`.
   - Enter a name, command sequence, and assign a shortcut if desired.
   
2. **Execute Snippet:**
   - Use the assigned shortcut or click on the snippet in the panel to execute.

### 🔄 Tab Completion
- **Commands & Directories:** Press `Tab` to autocomplete commands and directory names.
- **Executables:** Autocomplete executable names within your current environment.

---

## 🔍 Listening Features

Remotely Terminal listens to various in-game events and user inputs to provide a seamless terminal experience:

- **Key Bindings:** Listens for key presses to open the terminal, execute snippets, and navigate through command history.
- **GUI Events:** Handles mouse clicks and scrolls within the terminal and snippets panels for interactions.
- **Process Streams:** Continuously listens to terminal process outputs and updates the GUI in real-time.
- **SSH Session:** Monitors SSH connections to manage session states and handle remote command executions.

---

## 📚 Mini Wiki

### 🛠️ Setting Up SSH

1. **Ensure SSH is Enabled on Remote Server:**
   - Verify that the SSH service is running on your target server.

2. **Connect via Terminal:**
   - Use the `ssh user@host` command within Remotely Terminal to establish a connection.
   
3. **Managing Sessions:**
   - Multiple SSH sessions can be managed through separate terminal tabs.

### 📝 Managing Command Snippets

- **Creating a Snippet:**
  - Open the snippets panel (Top Right).
  - Click `Create Snippet` and fill in the details.
  
- **Editing a Snippet:**
  - Right-click on an existing snippet to `Edit`.
  -  Delete, Modify the name, commands, or shortcut as needed.

### 🔄 Navigating Command History

- **Accessing History:**
  - Use the `Up` and `Down` arrow keys to navigate through previously entered commands.

### 🎨 Customizing the Interface

- **Resizing Panels:**
  - Drag the edges of the terminal or snippets panels to adjust their size.

---

## 🤝 Contributing

Contributions are welcome! Whether it's reporting bugs, suggesting features, or submitting pull requests, your help is greatly appreciated.

1. **Fork the Repository**
2. **Create a Branch** 
3. **Commit Your Changes** 
4. **Push to the Branch** 
5. **Open a Pull Request**

---

## 📜 License

This project is licensed under the [CC0 1.0 Universal License
](LICENSE).

---

## 📫 Contact

For any inquiries or support, feel free to reach out:

- **Discord:** [RedxAx Studios](https://dsc.gg/RedxAxStudios)

---

<p align="center">
  <img src="https://github.com/redxax/RemotelyTerminal/blob/main/assets/screenshot.png" alt="Remotely Terminal Screenshot" width="800"/>
</p>
