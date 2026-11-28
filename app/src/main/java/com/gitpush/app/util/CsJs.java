package com.gitpush.app.util;

/** Builds the small JavaScript snippets the app injects into VS Code Web (keys, text, viewport). Pure string logic. */
public final class CsJs {
    private CsJs() {}

    public static String jsString(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
                default: sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    /** Shared helpers installed once per page. */
    public static final String INSTALL =
            "(function(){if(window.__gp)return;var g=window.__gp={};"
          + "g.target=function(){var a=document.activeElement;if(a&&a!==document.body)return a;"
          + "return document.querySelector('.xterm-helper-textarea')||document.querySelector('textarea.inputarea')||document.body;};"
          + "g.key=function(k,code,kc,ctrl,alt,shift){var t=g.target();var o={key:k,code:code,keyCode:kc,which:kc,ctrlKey:!!ctrl,altKey:!!alt,shiftKey:!!shift,bubbles:true,cancelable:true,composed:true};"
          + "var d=new KeyboardEvent('keydown',o);try{Object.defineProperty(d,'keyCode',{get:function(){return kc}});Object.defineProperty(d,'which',{get:function(){return kc}});}catch(e){}"
          + "t.dispatchEvent(d);var u=new KeyboardEvent('keyup',o);t.dispatchEvent(u);};"
          + "g.text=function(s){var t=g.target();try{t.focus();}catch(e){}"
          + "var ok=false;try{ok=document.execCommand('insertText',false,s);}catch(e){}"
          + "if(!ok){g.paste(s);}};"
          + "g.paste=function(s){var t=g.target();try{t.focus();}catch(e){}"
          + "var dt=new DataTransfer();dt.setData('text/plain',s);var ev=new ClipboardEvent('paste',{clipboardData:dt,bubbles:true,cancelable:true});t.dispatchEvent(ev);};"
          + "g.vp=function(w){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}"
          + "m.setAttribute('content',w>0?('width='+w+', initial-scale=1, user-scalable=yes'):'width=device-width, initial-scale=1, user-scalable=yes');};"
          + "})();";

    /** key(name, code, keyCode, ctrl, alt, shift) */
    public static String key(String key, String code, int keyCode, boolean ctrl, boolean alt, boolean shift) {
        return INSTALL + "window.__gp.key(" + jsString(key) + "," + jsString(code) + "," + keyCode + "," + ctrl + "," + alt + "," + shift + ");";
    }

    public static String text(String s) { return INSTALL + "window.__gp.text(" + jsString(s) + ");"; }
    public static String paste(String s) { return INSTALL + "window.__gp.paste(" + jsString(s) + ");"; }
    public static String viewport(int width) { return INSTALL + "window.__gp.vp(" + width + ");"; }

    /** Presses Ctrl+<letter>. */
    public static String ctrlLetter(char c) {
        char u = Character.toUpperCase(c);
        return key(String.valueOf(Character.toLowerCase(c)), "Key" + u, u, true, false, false);
    }

    /** Selected text of the page, or of the focused field. */
    public static final String SELECTION = "(function(){var s=window.getSelection?window.getSelection().toString():'';"
            + "if(!s){var a=document.activeElement;if(a&&a.value!==undefined&&a.selectionStart!==a.selectionEnd)s=a.value.substring(a.selectionStart,a.selectionEnd);}return s;})()";
}
