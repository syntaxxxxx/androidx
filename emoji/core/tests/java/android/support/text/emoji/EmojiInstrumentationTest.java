/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.support.text.emoji;

import static android.support.text.emoji.util.Emoji.EMOJI_SINGLE_CODEPOINT;
import static android.support.text.emoji.util.Emoji.EMOJI_WITH_ZWJ;
import static android.support.text.emoji.util.EmojiMatcher.hasEmoji;
import static android.support.text.emoji.util.EmojiMatcher.hasEmojiAt;
import static android.support.text.emoji.util.EmojiMatcher.hasEmojiCount;
import static android.support.text.emoji.util.KeyboardUtil.del;
import static android.support.text.emoji.util.KeyboardUtil.forwardDel;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.text.emoji.test.R;
import android.support.text.emoji.util.KeyboardUtil;
import android.support.text.emoji.util.TestString;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EmojiInstrumentationTest {

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<>(
            TestActivity.class);
    private Instrumentation mInstrumentation;

    @BeforeClass
    public static void setupEmojiCompat() {
        EmojiCompat.reset(TestConfigBuilder.config());
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testGetSize_withRelativeSizeSpan() throws Exception {
        final TestActivity activity = mActivityRule.getActivity();
        final TextView textView = (TextView) activity.findViewById(R.id.text);

        // create a string with single codepoint emoji
        final TestString string = new TestString(EMOJI_SINGLE_CODEPOINT).withPrefix().withSuffix();
        final CharSequence charSequence = EmojiCompat.get().process(string.toString());
        assertNotNull(charSequence);
        assertThat(charSequence, hasEmojiCount(1));

        final Spannable spanned = (Spannable) charSequence;
        final EmojiSpan[] spans = spanned.getSpans(0, charSequence.length(), EmojiSpan.class);
        final EmojiSpan span = spans[0];

        // set text to the charSequence with the EmojiSpan
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                textView.setText(charSequence);
            }
        });
        mInstrumentation.waitForIdleSync();

        // record height of the default span
        final int defaultHeight = span.getHeight();

        // cover the charsequence with RelativeSizeSpan which will triple the size of the
        // characters.
        final RelativeSizeSpan sizeSpan = new RelativeSizeSpan(3.0f);
        spanned.setSpan(sizeSpan, 0, charSequence.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // set the new text
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                textView.setText(charSequence);
            }
        });
        mInstrumentation.waitForIdleSync();

        // record the height measured after RelativeSizeSpan
        final int heightWithRelativeSpan = span.getHeight();

        // accept 1sp error rate.
        final float delta = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1,
                mInstrumentation.getTargetContext().getResources().getDisplayMetrics());
        assertEquals(defaultHeight * 3, heightWithRelativeSpan, delta);
    }

    @Test
    public void testAppendWithSoftKeyboard() throws Exception {
        TestActivity activity = mActivityRule.getActivity();
        final EditText editText = (EditText) activity.findViewById(R.id.editText);
        final TestString string = new TestString(EMOJI_WITH_ZWJ).withPrefix()
                .withSuffix();
        final InputConnection inputConnection = editText.onCreateInputConnection(new EditorInfo());
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.setComposingTextInBatch(inputConnection, string.toString());
            }
        });
        mInstrumentation.waitForIdleSync();

        Editable editable = editText.getEditableText();

        // 0xf0950 is the remapped codepoint for WOMEN_WITH_BALL
        assertThat(editable, hasEmojiAt(EMOJI_WITH_ZWJ, string.emojiStartIndex(),
                string.emojiEndIndex()));
    }

    @Test
    public void testBackDeleteWithSoftKeyboard() throws Exception {
        TestActivity activity = mActivityRule.getActivity();
        final EditText editText = (EditText) activity.findViewById(R.id.editText);
        final TestString string = new TestString(EMOJI_WITH_ZWJ).withPrefix()
                .withSuffix();
        final InputConnection inputConnection = editText.onCreateInputConnection(new EditorInfo());
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.setComposingTextInBatch(inputConnection, string.toString());
                Selection.setSelection(editText.getEditableText(), string.emojiEndIndex());
            }
        });
        mInstrumentation.waitForIdleSync();
        final Editable editable = editText.getEditableText();
        assertThat(editable, hasEmoji());

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.deleteSurrondingText(inputConnection, 1, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertThat(editable, not(hasEmoji()));
    }

    @Test
    public void testForwardDeleteWithSoftKeyboard() throws Exception {
        TestActivity activity = mActivityRule.getActivity();
        final EditText editText = (EditText) activity.findViewById(R.id.editText);
        final TestString string = new TestString(EMOJI_WITH_ZWJ).withPrefix()
                .withSuffix();
        final InputConnection inputConnection = editText.onCreateInputConnection(new EditorInfo());
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.setComposingTextInBatch(inputConnection, string.toString());
                Selection.setSelection(editText.getEditableText(), string.emojiStartIndex());
            }
        });
        mInstrumentation.waitForIdleSync();
        final Editable editable = editText.getEditableText();
        assertThat(editable, hasEmoji());

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.deleteSurrondingText(inputConnection, 0, 1);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertThat(editable, not(hasEmoji()));
    }

    @Test
    public void testBackDeleteWithHardwareKeyboard() throws Exception {
        TestActivity activity = mActivityRule.getActivity();
        final EditText editText = (EditText) activity.findViewById(R.id.editText);
        final TestString string = new TestString(EMOJI_WITH_ZWJ).withPrefix()
                .withSuffix();
        final InputConnection inputConnection = editText.onCreateInputConnection(new EditorInfo());
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.setComposingTextInBatch(inputConnection, string.toString());
                Selection.setSelection(editText.getEditableText(), string.emojiEndIndex());
            }
        });
        mInstrumentation.waitForIdleSync();
        final Editable editable = editText.getEditableText();
        assertThat(editable, hasEmoji());


        mInstrumentation.sendKeySync(del());
        mInstrumentation.waitForIdleSync();
        assertThat(editable, not(hasEmoji()));
    }

    @Test
    public void testForwardDeleteWithHardwareKeyboard() throws Exception {
        TestActivity activity = mActivityRule.getActivity();
        final EditText editText = (EditText) activity.findViewById(R.id.editText);
        final TestString string = new TestString(EMOJI_WITH_ZWJ).withPrefix()
                .withSuffix();
        final InputConnection inputConnection = editText.onCreateInputConnection(new EditorInfo());
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                KeyboardUtil.setComposingTextInBatch(inputConnection, string.toString());
                Selection.setSelection(editText.getEditableText(), string.emojiStartIndex());
            }
        });
        mInstrumentation.waitForIdleSync();
        final Editable editable = editText.getEditableText();
        assertThat(editable, hasEmoji());

        mInstrumentation.sendKeySync(forwardDel());
        mInstrumentation.waitForIdleSync();
        assertThat(editable, not(hasEmoji()));
    }
}
