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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class BWACEditorComponent implements SelectionListener, CaretListener, DocumentListener {
    private Editor editor;
    private final List<RangeHighlighter> items = new ArrayList<RangeHighlighter>();
    private volatile int updating;

    private static final int HIGHLIGHTLAYER = HighlighterLayer.SELECTION - 1; // unmittelbar unter Selektion-level
    private static final boolean AUTOHIGHLIGHT = Boolean.parseBoolean(System.getProperty("browseWordAtCaret.autoHighlight")); // experimental
    private static final int UPDATEDELAY = 400;

    public BWACEditorComponent(Editor editor) {
        this.editor = editor;

        editor.getSelectionModel().addSelectionListener(this);
        editor.getCaretModel().addCaretListener(this);
        editor.getDocument().addDocumentListener(this);
    }

    public void dispose() {
//        clearHighlighters();
        editor.getSelectionModel().removeSelectionListener(this);
        editor.getCaretModel().removeCaretListener(this);
        editor.getDocument().removeDocumentListener(this);
//        editor = null;
    }

    @Override
    public void selectionChanged(SelectionEvent selectionEvent) {
        if (updating > 0) {
            return;
        }

        // wenn ColumnMode -> gibts nicht mehr zu tun
        if (editor.isColumnMode()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    buildHighlighters(null); // noch löschen, da vielleicht etwas selektiert war und umgestellt wurde
                }
            });
            return;
        }
        // Selektion wurde aufgehoben -> nichts machen -> sobald cursor ausserhalb kommt wird ge'cleared... ( siehe caretPositionChanged...)
        if (selectionEvent.getNewRange().getLength() == 0) {
            return;
        }

        String text = editor.getDocument().getText();
        TextRange textRange = selectionEvent.getNewRange();

        // aufgrund selektiertem Text erstellen
        final String highlightText;
        if ((textRange.getStartOffset() != 0 || textRange.getEndOffset() != text.length()) && // fix issue 5: komplettem text ausschliessen
                BWACUtils.isStartEnd(text, textRange.getStartOffset(), textRange.getEndOffset(), false)) {
            highlightText = textRange.substring(text);
        } else {
            highlightText = null; // ansonsten löschen
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                buildHighlighters(highlightText);
            }
        });
    }

    private Timer timer = new Timer(UPDATEDELAY, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            synchronized (items) {
                int currentOffset = editor.getCaretModel().getOffset();
                // wenn der Cursor innerhalb eines unserer RangeHighlighter kommt -> return
                if (!items.isEmpty() && getItemIndex(currentOffset) >= 0) {
                    return;
                }

                // aktuelles Wort unter dem Cursor nehmen...
                final String wordToHighlight;
                if (!editor.getSelectionModel().hasSelection() && AUTOHIGHLIGHT) {
                    wordToHighlight = BWACUtils.extractWordFrom(editor.getDocument().getText(), currentOffset);
                } else {
                    wordToHighlight = null;
                }

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        buildHighlighters(StringUtil.isEmpty(wordToHighlight) ? null : wordToHighlight);
                    }
                });
            }
        }
    }) {
        {
            setRepeats(false);
        }
    };

    @Override
    public void caretPositionChanged(CaretEvent caretEvent) {
        if (updating > 0) {
            return;
        }
        timer.restart();
    }

    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                buildHighlighters(null); // bei changed -> löschen
            }
        });
    }

    public void browse(final BWACHandlerBrowse.BrowseDirection browseDirection) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updating++;
                try {
                    synchronized (items) {
                        // wenn noch keine RangeHighlights vorhanden ->
                        if (items.isEmpty() || timer.isRunning()) {
                            // aktuelles Wort unter dem Cursor nehmen...
                            String currentWord = BWACUtils.extractWordFrom(editor.getDocument().getText(), editor.getCaretModel().getOffset());
                            if (currentWord == null) {
                                return; // kein wort -> nichts zu machen
                            }
                            buildHighlighters(currentWord);
                        }
                        int index = getItemIndex(editor.getCaretModel().getOffset()) + (BWACHandlerBrowse.BrowseDirection.NEXT.equals(browseDirection) ? 1 : -1);

                        if (index >= 0 && index < items.size()) {
                            int offset = items.get(index).getStartOffset();
                            // Cursor setzen
                            editor.getCaretModel().moveToOffset(offset);
                            /*
                            // Wort selektieren
                            if (ApplicationManager.getApplication().getComponent(BWACApplicationComponent.class).prefSelectWord) {
                                editor.getSelectionModel().setSelection(offset, offset + highlightText.length());
                            }
                            */
                            // in sichtbaren Bereich bringen
                            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        }
                    }
                } finally {
                    updating--;
                }
            }
        });
    }

    private int getItemIndex(int offset) {
        synchronized (items) {
            for (int i = 0; i < items.size(); i++) {
                RangeHighlighter item = items.get(i);
                if (offset >= item.getStartOffset() && offset <= item.getEndOffset()) {
                    return i;
                }
            }
        }
        return -1;
    }

    // DISPATCH THREAD METHODS

    private void buildHighlighters(final String highlightText) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        synchronized (items) {
            // aktuelle löschen
            final MarkupModel markupModel = editor.getMarkupModel();
            for (RangeHighlighter rangeHighlighter : items) {
                markupModel.removeHighlighter(rangeHighlighter);
            }
            items.clear();
            // und erstellen
            if (highlightText != null) {
                // text durchsuchen
                String text = editor.getDocument().getText();
                // textAttribute für RangeHighlighter holen
                final TextAttributes textAttributes = editor.getColorsScheme().getAttributes(BWACColorSettingsPage.BROWSEWORDATCARET);

                int index = -1;
                do {
                    index = text.indexOf(highlightText, index + 1);
                    // wenn gefunden und ganzes wort -> aufnehmen
                    if (index >= 0 && BWACUtils.isStartEnd(text, index, index + highlightText.length(), true)) {
                        RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(index, index + highlightText.length(), HIGHLIGHTLAYER, textAttributes, HighlighterTargetArea.EXACT_RANGE);
                        rangeHighlighter.setErrorStripeTooltip(highlightText);
                        items.add(rangeHighlighter);
                    }
                } while (index >= 0);
            }
        }
    }
}
