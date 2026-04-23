package com.evalvis.sidecar.election;

import java.util.LinkedHashMap;
import java.util.Map;

final class SimpleJson {

    private SimpleJson() {}

    static String serialize(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String s) {
                sb.append("\"").append(s).append("\"");
            } else if (entry.getValue() == null) {
                sb.append("null");
            } else {
                sb.append(entry.getValue());
            }
        }
        sb.append("}");
        return sb.toString();
    }

    static Map<String, String> deserialize(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null) return result;
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        int i = 0;
        while (i < content.length()) {
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) i++;
            if (i >= content.length() || content.charAt(i) != '"') break;

            int keyStart = i + 1;
            int keyEnd = content.indexOf('"', keyStart);
            if (keyEnd < 0) break;
            String key = content.substring(keyStart, keyEnd);
            i = keyEnd + 1;

            while (i < content.length() && (content.charAt(i) == ':' || Character.isWhitespace(content.charAt(i)))) i++;
            if (i >= content.length()) break;

            String value;
            if (content.charAt(i) == '"') {
                int valueStart = i + 1;
                int valueEnd = content.indexOf('"', valueStart);
                if (valueEnd < 0) break;
                value = content.substring(valueStart, valueEnd);
                i = valueEnd + 1;
            } else {
                int valueStart = i;
                while (i < content.length() && content.charAt(i) != ',' && content.charAt(i) != '}') i++;
                value = content.substring(valueStart, i).trim();
            }

            result.put(key, value);
            while (i < content.length() && (content.charAt(i) == ',' || Character.isWhitespace(content.charAt(i)))) i++;
        }
        return result;
    }
}
