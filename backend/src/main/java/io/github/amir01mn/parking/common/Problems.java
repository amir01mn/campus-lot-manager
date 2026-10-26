package io.github.amir01mn.parking.common;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Factory for the application's RFC 7807 responses; {@code type} URIs are {@code urn:campus-parking:problem:<slug>}. */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:campus-parking:problem:";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_PREFIX + type));
        pd.setTitle(title);
        return pd;
    }
}
