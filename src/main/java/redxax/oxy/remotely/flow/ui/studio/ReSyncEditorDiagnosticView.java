package redxax.oxy.remotely.flow.ui.studio;

import restudio.resync.flow.contract.EditorError;

public interface ReSyncEditorDiagnosticView {
    void applyEditorError(EditorError error);

    void clearEditorError();
}
