package com.spimex.tz;

import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Organization(@JsonProperty("INN") String inn, @JsonProperty("BlockDate") String blockDate, @JsonProperty("Residence") String residence) {
}
