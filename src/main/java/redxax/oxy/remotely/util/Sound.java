package redxax.oxy.remotely.util;

public enum Sound {
    CLICK("click.ogg"),
    RIGHTCLICK("rightClick.ogg"),
    SELECT("select.ogg"),
    START("start.ogg"),
    STOP("stop.ogg"),
    INFO("info.ogg"),
    WARN("warn.ogg"),
    ERROR("error.ogg"),
    SUCCESS("success.ogg"),
    CREATE("create.ogg"),
    DELETE("delete.ogg"),
    COPY("copy.ogg"),
    PASTE("paste.ogg"),
    UNDO("undo.ogg"),
    REDO("redo.ogg"),
    SEND("send.ogg"),
    RECEIVE("receive.ogg"),
    RECEIVEERROR("receiveError.ogg"),
    SWITCHTAB("switchTab.ogg"),
    CLOSETAB("closeTab.ogg"),
    TERMINAL("terminal.ogg"),
    FILEEXPLORER("fileExplorer.ogg"),
    FILEEDITOR("fileEditor.ogg"),
    SERVERMANAGER("serverManager.ogg"),
    PANEL("panel.ogg"),
    SEARCH("search.ogg"),
    SAVE("save.ogg"),
    SCREEN("screen.ogg");


    private final String path;
    public static boolean enableSFX = true;
    public static int pitchVariation = 20;
    public static int soundVolume = 50;

    public static boolean soundCLICK = true;
    public static boolean soundRIGHTCLICK = true;
    public static boolean soundSELECT = true;
    public static boolean soundSTART = true;
    public static boolean soundSTOP = true;
    public static boolean soundINFO = true;
    public static boolean soundWARN = true;
    public static boolean soundERROR = true;
    public static boolean soundSUCCESS = true;
    public static boolean soundCREATE = true;
    public static boolean soundDELETE = true;
    public static boolean soundCOPY = true;
    public static boolean soundPASTE = true;
    public static boolean soundUNDO = true;
    public static boolean soundREDO = true;
    public static boolean soundSEND = true;
    public static boolean soundRECEIVE = true;
    public static boolean soundRECEIVEERROR = true;
    public static boolean soundSWITCHTAB = true;
    public static boolean soundCLOSETAB = true;
    public static boolean soundTERMINAL = true;
    public static boolean soundFILEEXPLORER = true;
    public static boolean soundFILEEDITOR = true;
    public static boolean soundSERVERMANAGER = true;
    public static boolean soundPANEL = true;
    public static boolean soundSEARCH = true;
    public static boolean soundSAVE = true;
    public static boolean soundSCREEN = true;


    Sound(String path) {
        this.path = path;
    }

    public String getPath() {
        return "/assets/remotely/sounds/" + path;
    }

    public boolean isEnabled() {
        if (!enableSFX) return false;
        return switch (this) {
            case CLICK -> soundCLICK;
            case RIGHTCLICK -> soundRIGHTCLICK;
            case SELECT -> soundSELECT;
            case START -> soundSTART;
            case STOP -> soundSTOP;
            case INFO -> soundINFO;
            case WARN -> soundWARN;
            case ERROR -> soundERROR;
            case SUCCESS -> soundSUCCESS;
            case CREATE -> soundCREATE;
            case DELETE -> soundDELETE;
            case COPY -> soundCOPY;
            case PASTE -> soundPASTE;
            case UNDO -> soundUNDO;
            case REDO -> soundREDO;
            case SEND -> soundSEND;
            case RECEIVE -> soundRECEIVE;
            case RECEIVEERROR -> soundRECEIVEERROR;
            case SWITCHTAB -> soundSWITCHTAB;
            case CLOSETAB -> soundCLOSETAB;
            case TERMINAL -> soundTERMINAL;
            case FILEEXPLORER -> soundFILEEXPLORER;
            case FILEEDITOR -> soundFILEEDITOR;
            case SERVERMANAGER -> soundSERVERMANAGER;
            case PANEL -> soundPANEL;
            case SEARCH -> soundSEARCH;
            case SAVE -> soundSAVE;
            case SCREEN -> soundSCREEN;
        };
    }
}