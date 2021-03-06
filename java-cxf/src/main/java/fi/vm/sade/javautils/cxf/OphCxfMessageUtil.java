package fi.vm.sade.javautils.cxf;

import org.apache.cxf.message.Message;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OphCxfMessageUtil {
    public static List<String> getHeader(Message message, String name) {
        Map<String, List<String>> headers = getHeadersThatMayBeNull(message);
        return headers == null ? Collections.emptyList() : headers.getOrDefault(name, Collections.emptyList());
    }

    public static void addHeader(Message message, String name, String value) {
        resolveHeaders(message).put(name, Collections.singletonList(value));
    }

    public static void appendToHeader(Message message, String headerName, String valueToAppend, String separator) {
        Map<String, List<String>> headers = resolveHeaders(message);
        List<String> originalValues = headers.getOrDefault(headerName, new LinkedList<>());
        if (originalValues.isEmpty()) {
            headers.put(headerName, Collections.singletonList(valueToAppend));
            return;
        }
        headers.put(headerName, originalValues.stream().map(original -> {
            if (original == null) {
                return valueToAppend;
            } else {
                return original + separator + valueToAppend;
            }
        }).collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> resolveHeaders(Message message) {
        Map<String, List<String>> outHeaders = getHeadersThatMayBeNull(message);
        if (outHeaders == null) {
            outHeaders = new HashMap<>();
            message.put(Message.PROTOCOL_HEADERS, outHeaders);
        }
        return outHeaders;
    }

    private static Map<String, List<String>> getHeadersThatMayBeNull(Message message) {
        return (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
    }
}
