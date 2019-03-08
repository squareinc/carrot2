package org.carrot2.dcs.client;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClusterRequest {
  public static class Document {
    public Map<String, String> fields = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, String> getFields() {
      return fields;
    }

    @JsonAnySetter
    public void setField(String field, String value) {
      fields.put(field, value);
    }
  }

  @JsonProperty
  public String language;

  @JsonProperty
  public String algorithm;

  @JsonProperty
  public List<Document> documents = new ArrayList<>();
}