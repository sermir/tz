package com.spimex.tz;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResultOrganizations(@JsonProperty("result") String result, @JsonProperty("records") Organization[] records ) {
}
