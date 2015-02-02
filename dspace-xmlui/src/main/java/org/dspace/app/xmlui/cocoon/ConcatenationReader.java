/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 * 
 * modified for LINDAT/CLARIN
 */
package org.dspace.app.xmlui.cocoon;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.ResourceNotFoundException;
import org.apache.cocoon.environment.*;
import org.apache.cocoon.reading.ResourceReader;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceValidity;
import org.apache.excalibur.source.impl.validity.TimeStampValidity;
import org.apache.log4j.Logger;
import org.dspace.core.ConfigurationManager;
import org.mozilla.javascript.EvaluatorException;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.*;

/**
 * Concatenates and Minifies CSS, JS and JSON files
 *
 * The URL of the resource can contain references to multiple
 * files: e.g."themes/Mirage/lib/css/reset,base,helper,style,print.css"
 * The Reader will concatenate all these files, and output them as
 * a single resource.
 *
 * If "xmlui.theme.enableMinification" is set to true, the
 * output will also be minified prior to returning the resource.
 *
 * Validity is determined based upon last modified date of
 * the most recently edited file.
 *
 * @author Roel Van Reeth (roel at atmire dot com)
 * @author Art Lowel (art dot lowel at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * modified for LINDAT/CLARIN
 */

public class ConcatenationReader extends ResourceReader {

    private static final int MINIFY_LINEBREAKPOS = 8000;
    protected List<Source> inputSources;
    private String key;
    private StreamEnumeration streamEnumeration;
    private static Logger log = Logger.getLogger(ConcatenationReader.class);
    private boolean doMinify = true;

    /**
     * Setup the reader.
     * The resource is opened to get an <code>InputStream</code>,
     * the length and the last modification date
     */
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
            throws ProcessingException, SAXException, IOException {

        // save key
        this.key = src;

        // don't support byte ranges (resumed downloads)
        this.setByteRanges(false);

        // setup list of sources, get relevant parts of path
        this.inputSources = new ArrayList<Source>();
        
        String path;
        String file;
        if(src.lastIndexOf('/') >= 0) {
            path = src.substring(0, src.lastIndexOf('/'));
            file = src.substring(src.lastIndexOf('/')+1);
        }
        else {
            path = "";
            file = src;
        }        

        // now build own list of inputsources
        String[] files = file.split(",");
        for (String f : files) {
            if (file.endsWith(".json") && !f.endsWith(".json")) {
                f += ".json";
            }
            if (file.endsWith(".js") && !f.endsWith(".js")) {
                f += ".js";
            }
            if (file.endsWith(".css") && !f.endsWith(".css")) {
                f += ".css";
            }

            String fullPath = path + "/" + f;
            this.inputSources.add(resolver.resolveURI(fullPath));
        }

        int index = 0;
        for (Iterator<Source> it=this.inputSources.iterator(); it.hasNext();) {
            Source source = it.next();
            try
            {
                InputStream stream = source.getInputStream();
            } catch (IOException e) {
                String uri = null != source ? source.getURI() : "null";
                log.error(String.format("IOException in ConcatenationReader.setup when retrieving InputStream [%s]" +
                        " of a Source; index = %d, inputSources.size = %d; %s", uri, index, inputSources.size(), getRequestInfo(objectModel)));
                throw new ResourceNotFoundException(uri);
            }
            index++;
        }

        // do super stuff
        super.setup(resolver, objectModel, path+"/"+files[files.length-1], par);

        // add stream enumerator
        this.streamEnumeration = new StreamEnumeration();

        // check minify parameter
        try {
            if("nominify".equals(par.getParameter("requestQueryString"))) {
                this.doMinify = false;
            } else {
                // modify key!
                this.key += "?minify";
            }
        } catch (ParameterException e) {
            log.error("ParameterException in setup when retrieving parameter requestQueryString", e);
        }
    }

    /**
     * Returns information about the referrer (remote IP address and referrer)
     * 
     * @return Information about the referrer
     */
    private String getRequestInfo(Map objectModel)
    {
        String referrer = null;
        String ipAddress = null;
        String requestURI = null;

        Request request = ObjectModelHelper.getRequest(objectModel);

        if (request != null)
        {
            referrer = request.getHeader("Referer"); // Misspelled header name
            ipAddress = request.getRemoteAddr();
            requestURI = request.getRequestURI();
        }
        referrer = referrer != null ? referrer : "?";

        String requestInfo = String.format(
                "request URI = %s, remote address = %s, referrer = %s",
                requestURI, ipAddress, referrer);

        return requestInfo;
    }

    /**
     * Recyclable
     */
    public void recycle() {
        if (this.inputSources != null) {
            for(Source s : this.inputSources) {
                super.resolver.release(s);
            }
            this.inputSources = null;
            this.streamEnumeration = null;
            this.key = null;
        }
        super.recycle();
    }

    /**
     * Generate the unique key.
     * This key must be unique inside the space of this component.
     *
     * @return The generated key hashes the src
     */
    public Serializable getKey() {
        return key;
    }

    /**
     * Generate the validity object.
     *
     * @return The generated validity object or <code>null</code> if the
     *         component is currently not cacheable.
     */
    public SourceValidity getValidity() {
        final long lm = getLastModified();
        if(lm > 0) {
            return new TimeStampValidity(lm);
        }
        return null;
    }

    /**
     * @return the time the read source was last modified or 0 if it is not
     *         possible to detect
     */
    public long getLastModified() {
        // get latest modified value
        long modified = 0;

        for(Source s : this.inputSources) {
            if(s.getLastModified() > modified) {
                modified = s.getLastModified();
            }
        }

        return modified;
    }

    /**
     * Generates the requested resource.
     */
    public void generate() throws IOException, ProcessingException {
        InputStream inputStream;

        // create one single inputstream from all files
        inputStream = new SequenceInputStream(streamEnumeration);

        try {
            if (ConfigurationManager.getBooleanProperty("xmlui.theme.enableMinification",false) && this.doMinify) {
                compressedOutput(inputStream);
            } else {
                normalOutput(inputStream);
            }
            // Bugzilla Bug #25069: Close inputStream in finally block.
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }

        out.flush();
    }

    private void compressedOutput(InputStream inputStream) throws IOException {
        // prepare streams
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Writer outWriter = new OutputStreamWriter(bytes);

        // do compression
        Reader in = new BufferedReader(new InputStreamReader(inputStream));
        if (this.key.endsWith(".js?minify") || this.key.endsWith(".json?minify")) {
            try {

                JavaScriptCompressor compressor = new JavaScriptCompressor(in, null);

                // boolean options: munge, verbose, preserveAllSemiColons, disableOptimizations
                compressor.compress(outWriter, MINIFY_LINEBREAKPOS, true, false, false, false);

            } catch (EvaluatorException e) {
                // fail gracefully on malformed javascript: send it without compressing
                normalOutput(inputStream);
                return;
            }
        } else if (this.key.endsWith(".css?minify")) {
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(outWriter, MINIFY_LINEBREAKPOS);
        } else {
            // or not if not right type
            normalOutput(inputStream);
            return;
        }

        // first send content-length header
        outWriter.flush();
        response.setHeader("Content-Length", Long.toString(bytes.size()));

        // then send output and clean up
        bytes.writeTo(out);
        in.close();
    }

    private void normalOutput(InputStream inputStream) throws IOException {
        boolean validContentLength = true;
        byte[] buffer = new byte[bufferSize];
        int length;
        long contentLength = 0;

        // calculate content length
        for (Source s : this.inputSources) {
            if(s.getContentLength() < 0) {
                validContentLength = false;
            }
            contentLength += s.getContentLength();
        }
        if(validContentLength) {
            response.setHeader("Content-Length", Long.toString(contentLength));
        }

        // send contents
        while ((length = inputStream.read(buffer)) > -1) {
            out.write(buffer, 0, length);
        }
    }

    private final class StreamEnumeration implements Enumeration {
        private int index;

        private StreamEnumeration() {
            this.index = 0;
        }

        public boolean hasMoreElements() {
            return index < inputSources.size();
        }

        public InputStream nextElement() {
            Source src = null;
            try {
                src = inputSources.get(index);
                InputStream elem = src.getInputStream();
                index++;
                return elem;
            } catch (IOException e) {
                String uri = null != src ? src.getURI() : "null";
                log.error(String.format("IOException in StreamEnumeration.nextElement when retrieving InputStream [%s]" +
                                        " of a Source; index = %d, inputSources.size = %d", uri, index, inputSources.size()));
                return null;
            }
        }
    }
}
