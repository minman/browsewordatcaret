/*
 * Copyright 2012 Minas Manthos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package browsewordatcaret;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class BWACApplicationComponent implements ApplicationComponent, EditorFactoryListener {
    private Map<Editor, BWACEditorComponent> editorComponents;

    @NonNls
    @Override
    @NotNull
    public String getComponentName() {
        return getClass().getSimpleName();
    }

    @Override
    public void initComponent() {
        editorComponents = new HashMap<Editor, BWACEditorComponent>();
        EditorFactory.getInstance().addEditorFactoryListener(this);

        // Copy
        final EditorActionHandler editorCopyHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_COPY);
        EditorActionManager.getInstance().setActionHandler(IdeActions.ACTION_EDITOR_COPY, new EditorActionHandler() {
            @Override
            public void execute(Editor editor, DataContext dataContext) {
                selectWordUnderCaret(editor);
                editorCopyHandler.execute(editor, dataContext);
            }
        });
        // Cut
        final EditorActionHandler editorCutHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_CUT);
        EditorActionManager.getInstance().setActionHandler(IdeActions.ACTION_EDITOR_CUT, new EditorActionHandler() {
            @Override
            public void execute(Editor editor, DataContext dataContext) {
                selectWordUnderCaret(editor);
                editorCutHandler.execute(editor, dataContext);
            }
        });
        // PasteReplace -> siehe BWACHandlerPasteReplace
    }

    public static void selectWordUnderCaret(Editor editor) {
        // wenn nichts selektiert ist -> Wort unter caret selektieren
        if (editor.getSelectionModel().getSelectedText() == null) {
            editor.getSelectionModel().selectWordAtCaret(false);
        }
    }

    @Override
    public void disposeComponent() {
        EditorFactory.getInstance().removeEditorFactoryListener(this);
        for (BWACEditorComponent editorComponent : editorComponents.values()) {
            editorComponent.dispose();
        }
        editorComponents.clear();
    }

    @Override
    public void editorCreated(EditorFactoryEvent editorFactoryEvent) {
        Editor editor = editorFactoryEvent.getEditor();
        if (editor.getProject() == null) {
            return;
        }
        BWACEditorComponent editorComponent = new BWACEditorComponent(editorFactoryEvent.getEditor());
        editorComponents.put(editorFactoryEvent.getEditor(), editorComponent);
    }

    @Override
    public void editorReleased(EditorFactoryEvent editorFactoryEvent) {
        BWACEditorComponent editorComponent = editorComponents.remove(editorFactoryEvent.getEditor());
        if (editorComponent == null) {
            return;
        }
        editorComponent.dispose();
    }

    public BWACEditorComponent getEditorComponent(Editor editor) {
        return editorComponents.get(editor);
    }
}