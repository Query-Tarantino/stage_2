package org.ulpgc.tarantino.common.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.ulpgc.tarantino.common.model.ProblemCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
record Problem(String type, String title, int status, ProblemCode code, String parameter, String detail) {
}
