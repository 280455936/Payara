package fish.payara.admin.rest.streams;

import java.io.IOException;
import java.io.OutputStream;
import javax.json.Json;
import javax.json.stream.JsonGenerationException;
import javax.json.stream.JsonGenerator;
import org.glassfish.admin.rest.Constants;

/**
 * A {@link StreamWriter} for handling JSON.
 */
public class JsonStreamWriter implements StreamWriter {

    private final String prefix;
    private final String postfix;
    private final OutputStream os;
    private final JsonGenerator writer;

    private boolean inArray;

    /**
     * Creates a {@link StreamWriter} for handling JSON.
     *
     * @param os The OutputStream to write to.
     * @param prefix Any data that needs writing at the start of the stream.
     * @param postfix Any data that needs writing at the end of the stream.
     */
    public JsonStreamWriter(OutputStream os, String prefix, String postfix) {
        this.prefix = prefix;
        this.postfix = postfix;
        this.os = os;
        this.writer = Json.createGenerator(os);
    }

    /**
     * Creates a {@link StreamWriter} for handling JSON, with a {@code null}
     * prefix and postfix.
     *
     * @param os The OutputStream to write to.
     */
    public JsonStreamWriter(OutputStream os) {
        this(os, null, null);
    }

    @Override
    public String getPostfix() {
        return postfix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public void writeStartDocument() throws JsonGenerationException, IOException {
        if (prefix != null) {
            os.write(prefix.getBytes(Constants.ENCODING));
        }
        writer.writeStartObject();
        inArray = false;
    }

    @Override
    public void writeEndDocument() throws JsonGenerationException, IOException {
        writer.writeEnd();
        if (postfix != null) {
            os.write(postfix.getBytes(Constants.ENCODING));
        }
    }

    @Override
    public void writeStartObject(String element) throws JsonGenerationException {
        // Objects inside arrays in JSON don't have names
        if (inArray) {
            writer.writeStartObject();
        } else {
            writer.writeStartObject(element);
        }
    }

    @Override
    public void writeEndObject() throws JsonGenerationException {
        writer.writeEnd();
    }

    @Override
    public void write(String value) throws JsonGenerationException {
        writer.write(value);
    }

    @Override
    public void writeAttribute(String name, String value) throws JsonGenerationException {
        writer.write('@' + name, value);
    }

    @Override
    public void writeAttribute(String name, Boolean value) throws JsonGenerationException {
        writer.write('@' + name, value);
    }

    @Override
    public void close() throws JsonGenerationException {
        writer.close();
    }

    @Override
    public void writeStartArray(String element) throws Exception {
        writer.writeStartArray(element);
        inArray = true;
    }

    @Override
    public void writeEndArray() throws Exception {
        writer.writeEnd();
        inArray = false;
    }

}
