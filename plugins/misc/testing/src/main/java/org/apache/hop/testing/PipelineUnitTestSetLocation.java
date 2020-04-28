/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.testing;

import org.apache.hop.metastore.persist.MetaStoreAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * This defines the place where we need to inject an input data set
 *
 * @author matt
 */
public class PipelineUnitTestSetLocation {

  @MetaStoreAttribute( key = "transform_name" )
  protected String transformName;

  @MetaStoreAttribute( key = "data_set_name" )
  protected String dataSetName;

  @MetaStoreAttribute( key = "field_mappings" )
  protected List<PipelineUnitTestFieldMapping> fieldMappings;

  @MetaStoreAttribute( key = "field_order" )
  protected List<String> fieldOrder;

  public PipelineUnitTestSetLocation() {
    fieldMappings = new ArrayList<>();
    fieldOrder = new ArrayList<>();
  }

  public PipelineUnitTestSetLocation( String transformName, String dataSetName, List<PipelineUnitTestFieldMapping> fieldMappings, List<String> fieldOrder ) {
    this();
    this.transformName = transformName;
    this.dataSetName = dataSetName;
    this.fieldMappings = fieldMappings;
    this.fieldOrder = fieldOrder;
  }

  public String findTransformField( String dataSetField ) {
    for ( PipelineUnitTestFieldMapping fieldMapping : fieldMappings ) {
      if ( fieldMapping.getDataSetFieldName().equalsIgnoreCase( dataSetField ) ) {
        return fieldMapping.getTransformFieldName();
      }
    }
    return null;
  }

  public String getTransformName() {
    return transformName;
  }

  public void setTransformName( String transformName ) {
    this.transformName = transformName;
  }

  public String getDataSetName() {
    return dataSetName;
  }

  public void setDataSetName( String dataSetName ) {
    this.dataSetName = dataSetName;
  }

  public List<PipelineUnitTestFieldMapping> getFieldMappings() {
    return fieldMappings;
  }

  public void setFieldMappings( List<PipelineUnitTestFieldMapping> fieldMappings ) {
    this.fieldMappings = fieldMappings;
  }

  public List<String> getFieldOrder() {
    return fieldOrder;
  }

  public void setFieldOrder( List<String> fieldOrder ) {
    this.fieldOrder = fieldOrder;
  }

}
