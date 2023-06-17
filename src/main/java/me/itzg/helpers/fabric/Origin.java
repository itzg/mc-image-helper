package me.itzg.helpers.fabric;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME)
@JsonSubTypes({
    @Type(value = Versions.class, name = "versions"),
    @Type(value = RemoteFile.class, name = "remote")
})
public abstract class Origin {

}
