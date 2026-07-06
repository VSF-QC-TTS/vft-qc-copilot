package vn.vinfast.vfqc.api.target.parser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

@Component
public class CurlParser {

  public ParsedCurlRequest parse(String rawCurl) {
    var tokens = tokenize(rawCurl);
    if (tokens.isEmpty() || !"curl".equals(tokens.getFirst())) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Input must start with curl");
    }
    var method = "GET";
    var url = "";
    var headers = new LinkedHashMap<String, String>();
    var body = "";
    for (var index = 1; index < tokens.size(); index++) {
      var token = tokens.get(index);
      if ("|".equals(token) || token.contains("$(") || token.contains("`")) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Unsupported cURL shell syntax");
      }
      switch (token) {
        case "-X", "--request" -> method = requireNext(tokens, ++index, token).toUpperCase();
        case "-H", "--header" -> addHeader(headers, requireNext(tokens, ++index, token));
        case "-d", "--data", "--data-raw", "--data-binary" -> {
          body = requireNext(tokens, ++index, token);
          if (body.startsWith("@")) {
            throw ErrorCode.VALIDATION_ERROR.throwIt("cURL file references are not supported");
          }
          if ("GET".equals(method)) {
            method = "POST";
          }
        }
        case "-F", "--form", "--form-string" ->
            throw ErrorCode.VALIDATION_ERROR.throwIt("cURL multipart/form upload is not supported");
        default -> {
          if (token.startsWith("@")) {
            throw ErrorCode.VALIDATION_ERROR.throwIt("cURL file references are not supported");
          }
          if (token.startsWith("http://") || token.startsWith("https://")) {
            url = token;
          }
        }
      }
    }
    if (url.isBlank()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("cURL URL is required");
    }
    var uri = URI.create(url);
    return new ParsedCurlRequest(
        method, withoutQuery(uri), headers, queryParams(uri.getRawQuery()), body);
  }

  private List<String> tokenize(String input) {
    var tokens = new ArrayList<String>();
    var current = new StringBuilder();
    var quote = '\0';
    for (var index = 0; index < input.length(); index++) {
      var c = input.charAt(index);
      if (quote != '\0') {
        if (c == quote) {
          quote = '\0';
        } else {
          current.append(c);
        }
        continue;
      }
      if (c == '\'' || c == '"') {
        quote = c;
        continue;
      }
      if (Character.isWhitespace(c)) {
        addToken(tokens, current);
        continue;
      }
      current.append(c);
    }
    if (quote != '\0') {
      throw ErrorCode.VALIDATION_ERROR.throwIt("cURL contains an unterminated quoted value");
    }
    addToken(tokens, current);
    return List.copyOf(tokens);
  }

  private void addToken(List<String> tokens, StringBuilder current) {
    if (!current.isEmpty()) {
      tokens.add(current.toString());
      current.setLength(0);
    }
  }

  private String requireNext(List<String> tokens, int index, String flag) {
    if (index >= tokens.size()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Missing value for " + flag);
    }
    return tokens.get(index);
  }

  private void addHeader(LinkedHashMap<String, String> headers, String rawHeader) {
    var separator = rawHeader.indexOf(':');
    if (separator <= 0) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Invalid cURL header");
    }
    headers.put(rawHeader.substring(0, separator).trim(), rawHeader.substring(separator + 1).trim());
  }

  private String withoutQuery(URI uri) {
    return URI.create(
            uri.getScheme()
                + "://"
                + uri.getRawAuthority()
                + (uri.getRawPath() == null || uri.getRawPath().isBlank() ? "" : uri.getRawPath()))
        .toString();
  }

  private Map<String, String> queryParams(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return Map.of();
    }
    var queryParams = new LinkedHashMap<String, String>();
    for (var pair : rawQuery.split("&")) {
      var separator = pair.indexOf('=');
      var key = separator < 0 ? pair : pair.substring(0, separator);
      var value = separator < 0 ? "" : pair.substring(separator + 1);
      queryParams.put(decode(key), decode(value));
    }
    return queryParams;
  }

  private String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
