package me.itzg.helpers.patch;

import com.jayway.jsonpath.JsonPathException;
import me.itzg.helpers.patch.model.PatchOperation;

public class PatchOperationException extends RuntimeException {

    public PatchOperationException(PatchOperation op, JsonPathException e) {
        super("Patch operation failed: " + op, e);
    }
}
