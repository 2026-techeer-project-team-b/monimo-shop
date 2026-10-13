package com.monimo.shop.agentextension;

import java.util.HashMap;
import java.util.Map;

/**
 * 아주 작은 JSON 도우미. Extension 에 라이브러리를 넣지 않으려고 직접 쓴다 (#34 research ⑧ : 의존성 0개).
 * 명령은 문자열 · 숫자 값만 있는 평평한 객체라 이걸로 충분하다. 중첩 객체 · 배열은 지원하지 않는다.
 */
final class Json {

    private Json() {}

    /** {"a":"x","b":10} → {a=x, b=10}. 모양이 틀리면 null */
    static Map<String, String> parseFlatObject(String s) {
        if (s == null) return null;
        int i = skip(s, 0);
        if (i >= s.length() || s.charAt(i) != '{') return null;
        Map<String, String> out = new HashMap<>();
        i = skip(s, i + 1);
        if (i < s.length() && s.charAt(i) == '}') return out;
        while (i < s.length()) {
            if (s.charAt(i) != '"') return null;
            int[] end = new int[1];
            String key = readString(s, i, end);
            if (key == null) return null;
            i = skip(s, end[0]);
            if (i >= s.length() || s.charAt(i) != ':') return null;
            i = skip(s, i + 1);
            String value;
            if (i < s.length() && s.charAt(i) == '"') {
                value = readString(s, i, end);
                if (value == null) return null;
                i = end[0];
            } else {
                int start = i;
                while (i < s.length() && ",} \t\r\n".indexOf(s.charAt(i)) < 0) i++;
                value = s.substring(start, i);
                if (value.isEmpty()) return null;
            }
            out.put(key, value);
            i = skip(s, i);
            if (i >= s.length()) return null;
            if (s.charAt(i) == '}') return out;
            if (s.charAt(i) != ',') return null;
            i = skip(s, i + 1);
        }
        return null;
    }

    /** 문자열을 JSON 문자열 값으로 ("…" 포함). 덤프 본문의 줄바꿈 · 따옴표 · 탭을 이스케이프한다 */
    static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    private static int skip(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static String readString(String s, int start, int[] end) {
        StringBuilder b = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                end[0] = i + 1;
                return b.toString();
            }
            if (c == '\\') {
                if (i + 1 >= s.length()) return null;
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'u' -> {
                        if (i + 4 >= s.length()) return null;
                        b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> b.append(n);
                }
            } else {
                b.append(c);
            }
            i++;
        }
        return null;
    }
}
