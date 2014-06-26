/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.jaxrs.ext.search.tika;

import java.io.InputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.cxf.jaxrs.ext.search.tika.TikaContentExtractor.TikaContent;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;

public class TikaLuceneContentExtractor {
    private final DocumentMetadata defaultDocumentMetadata;    
    private final TikaContentExtractor extractor;
    
    public static class DocumentMetadata {
        private final Map< String, Class< ? > > fieldTypes = 
            new LinkedHashMap< String, Class< ? > >();
        private final String contentFieldName;
        
        public DocumentMetadata(final String contentFieldName) {
            this.contentFieldName = contentFieldName;
        }
        
        public DocumentMetadata withField(final String name, final Class< ? > type) {
            fieldTypes.put(name, type);
            return this;
        }
        
        public String getContentFieldName() {
            return contentFieldName;
        }
        
        private Field contentField(final String content) {
            return new TextField(contentFieldName, content, Store.YES);
        }
        
        private Field field(final String name, final String value) {
            final Class< ? > type = fieldTypes.get(name);
            
            if (type != null) {
                if (Number.class.isAssignableFrom(type)) {
                    if (Double.class.isAssignableFrom(type)) {
                        return new DoubleField(name, Double.valueOf(value), Store.YES);
                    } else if (Float.class.isAssignableFrom(type)) {
                        return new FloatField(name, Float.valueOf(value), Store.YES);
                    } else if (Long.class.isAssignableFrom(type)) {
                        return new LongField(name, Long.valueOf(value), Store.YES);
                    } else if (Integer.class.isAssignableFrom(type)) {
                        return new IntField(name, Integer.valueOf(value), Store.YES);
                    }
                } else if (Date.class.isAssignableFrom(type)) {
                    return new StringField(name, value, Store.YES);
                }                
            }
            
            return new StringField(name, value, Store.YES);
        }
    }
    
    
    /**
     * Create new Tika-based content extractor using the provided parser instance.  
     * @param parser parser instance
     */
    public TikaLuceneContentExtractor(final Parser parser) {
        this(parser, true);
    }
    
    /**
     * Create new Tika-based content extractor using the provided parser instance and
     * optional media type validation. If validation is enabled, the implementation 
     * will try to detect the media type of the input and validate it against media types
     * supported by the parser.
     * @param parser parser instance
     * @param validateMediaType enabled or disable media type validation
     */
    public TikaLuceneContentExtractor(final Parser parser, final boolean validateMediaType) {
        this(parser, validateMediaType, "contents");
    }
    
    /**
     * Create new Tika-based content extractor using the provided parser instance and
     * optional media type validation. If validation is enabled, the implementation 
     * will try to detect the media type of the input and validate it against media types
     * supported by the parser.
     * @param parser parser instance
     * @param validateMediaType enabled or disable media type validation
     * @param contentFieldName name of the content field, default is "contents"
     */
    public TikaLuceneContentExtractor(final Parser parser, final boolean validateMediaType, 
                                final String contentFieldName) {
        extractor = new TikaContentExtractor(parser, validateMediaType);
        defaultDocumentMetadata = new DocumentMetadata(contentFieldName);
    }
    
    /**
     * Extract the content and metadata from the input stream. Depending on media type validation,
     * the detector could be run against input stream in order to ensure that parser supports this
     * type of content. 
     * @param in input stream to extract the content and metadata from  
     * @return the extracted document or null if extraction is not possible or was unsuccessful
     */
    public Document extract(final InputStream in) {
        return extractAll(in, defaultDocumentMetadata, true, true);
    }
    
    /**
     * Extract the content and metadata from the input stream using DocumentMetadata descriptor to
     * create a document with strongly typed fields. Depending on media type validation,
     * the detector could be run against input stream in order to ensure that parser supports this
     * type of content. 
     * @param in input stream to extract the content and metadata from  
     * @param metadata document descriptor with field names and their types
     * @return the extracted document or null if extraction is not possible or was unsuccessful
     */
    public Document extract(final InputStream in, final DocumentMetadata metadata) {
        return extractAll(in, metadata, true, true);
    }
    
    /**
     * Extract the content only from the input stream. Depending on media type validation,
     * the detector could be run against input stream in order to ensure that parser supports this
     * type of content. 
     * @param in input stream to extract the content from  
     * @return the extracted document or null if extraction is not possible or was unsuccessful
     */
    public Document extractContent(final InputStream in) {
        return extractAll(in, defaultDocumentMetadata, true, false);
    }
    
    /**
     * Extract the metadata only from the input stream. Depending on media type validation,
     * the detector could be run against input stream in order to ensure that parser supports this
     * type of content. 
     * @param in input stream to extract the metadata from  
     * @return the extracted document or null if extraction is not possible or was unsuccessful
     */
    public Document extractMetadata(final InputStream in) {
        return extractAll(in, defaultDocumentMetadata, false, true);
    }
    
    private Document extractAll(final InputStream in, final DocumentMetadata documentMetadata, 
            boolean extractContent, boolean extractMetadata) {
        
        TikaContent content = extractor.extractAll(in, extractContent);
        
        if (content == null) {
            return null;
        }
        final Document document = new Document();
        if (content.getContent() != null) {
            document.add(documentMetadata.contentField(content.getContent()));
        } 
        
        if (extractMetadata) {
            Metadata metadata = content.getMetadata();
            for (final String property: metadata.names()) {
                document.add(documentMetadata.field(property, metadata.get(property)));
            }
        }
        
        return document;
        
    }
}
