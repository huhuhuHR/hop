/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.trans.steps.fileexists;

import java.util.List;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopStepException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;

import org.apache.hop.shared.SharedObjectInterface;
import org.apache.hop.trans.Trans;
import org.apache.hop.trans.TransMeta;
import org.apache.hop.trans.step.BaseStepMeta;
import org.apache.hop.trans.step.StepDataInterface;
import org.apache.hop.trans.step.StepInterface;
import org.apache.hop.trans.step.StepMeta;
import org.apache.hop.trans.step.StepMetaInterface;
import org.apache.hop.metastore.api.IMetaStore;
import org.w3c.dom.Node;

/*
 * Created on 03-Juin-2008
 *
 */

public class FileExistsMeta extends BaseStepMeta implements StepMetaInterface {
  private static Class<?> PKG = FileExistsMeta.class; // for i18n purposes, needed by Translator2!!

  private boolean addresultfilenames;

  /** dynamic filename */
  private String filenamefield;

  private String filetypefieldname;

  private boolean includefiletype;

  /** function result: new value name */
  private String resultfieldname;

  public FileExistsMeta() {
    super(); // allocate BaseStepMeta
  }

  /**
   * @return Returns the filenamefield.
   */
  public String getDynamicFilenameField() {
    return filenamefield;
  }

  /**
   * @param filenamefield
   *          The filenamefield to set.
   */
  public void setDynamicFilenameField( String filenamefield ) {
    this.filenamefield = filenamefield;
  }

  /**
   * @return Returns the resultName.
   */
  public String getResultFieldName() {
    return resultfieldname;
  }

  /**
   * @param resultfieldname
   *          The resultfieldname to set.
   */
  public void setResultFieldName( String resultfieldname ) {
    this.resultfieldname = resultfieldname;
  }

  /**
   * @param filetypefieldname
   *          The filetypefieldname to set.
   */
  public void setFileTypeFieldName( String filetypefieldname ) {
    this.filetypefieldname = filetypefieldname;
  }

  /**
   * @return Returns the filetypefieldname.
   */
  public String getFileTypeFieldName() {
    return filetypefieldname;
  }

  public boolean includeFileType() {
    return includefiletype;
  }

  public boolean addResultFilenames() {
    return addresultfilenames;
  }

  public void setaddResultFilenames( boolean addresultfilenames ) {
    this.addresultfilenames = addresultfilenames;
  }

  public void setincludeFileType( boolean includefiletype ) {
    this.includefiletype = includefiletype;
  }

  public void loadXML( Node stepnode, IMetaStore metaStore ) throws HopXMLException {
    readData( stepnode, metaStore );
  }

  public Object clone() {
    FileExistsMeta retval = (FileExistsMeta) super.clone();

    return retval;
  }

  public void setDefault() {
    resultfieldname = "result";
    filetypefieldname = null;
    includefiletype = false;
    addresultfilenames = false;
  }

  public void getFields( RowMetaInterface inputRowMeta, String name, RowMetaInterface[] info, StepMeta nextStep,
    VariableSpace space, IMetaStore metaStore ) throws HopStepException {
    // Output fields (String)
    if ( !Utils.isEmpty( resultfieldname ) ) {
      ValueMetaInterface v =
        new ValueMetaBoolean( space.environmentSubstitute( resultfieldname ) );
      v.setOrigin( name );
      inputRowMeta.addValueMeta( v );
    }

    if ( includefiletype && !Utils.isEmpty( filetypefieldname ) ) {
      ValueMetaInterface v =
        new ValueMetaString( space.environmentSubstitute( filetypefieldname ) );
      v.setOrigin( name );
      inputRowMeta.addValueMeta( v );
    }
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder();

    retval.append( "    " + XMLHandler.addTagValue( "filenamefield", filenamefield ) );
    retval.append( "    " + XMLHandler.addTagValue( "resultfieldname", resultfieldname ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "includefiletype", includefiletype ) );
    retval.append( "    " + XMLHandler.addTagValue( "filetypefieldname", filetypefieldname ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "addresultfilenames", addresultfilenames ) );
    return retval.toString();
  }

  private void readData( Node stepnode, IMetaStore metaStore ) throws HopXMLException {
    try {
      filenamefield = XMLHandler.getTagValue( stepnode, "filenamefield" );
      resultfieldname = XMLHandler.getTagValue( stepnode, "resultfieldname" );
      includefiletype = "Y".equalsIgnoreCase( XMLHandler.getTagValue( stepnode, "includefiletype" ) );
      filetypefieldname = XMLHandler.getTagValue( stepnode, "filetypefieldname" );
      addresultfilenames = "Y".equalsIgnoreCase( XMLHandler.getTagValue( stepnode, "addresultfilenames" ) );
    } catch ( Exception e ) {
      throw new HopXMLException(
        BaseMessages.getString( PKG, "FileExistsMeta.Exception.UnableToReadStepInfo" ), e );
    }
  }

  public void check( List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepMeta,
    RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
    IMetaStore metaStore ) {
    CheckResult cr;
    String error_message = "";

    if ( Utils.isEmpty( resultfieldname ) ) {
      error_message = BaseMessages.getString( PKG, "FileExistsMeta.CheckResult.ResultFieldMissing" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_ERROR, error_message, stepMeta );
      remarks.add( cr );
    } else {
      error_message = BaseMessages.getString( PKG, "FileExistsMeta.CheckResult.ResultFieldOK" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_OK, error_message, stepMeta );
      remarks.add( cr );
    }
    if ( Utils.isEmpty( filenamefield ) ) {
      error_message = BaseMessages.getString( PKG, "FileExistsMeta.CheckResult.FileFieldMissing" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_ERROR, error_message, stepMeta );
      remarks.add( cr );
    } else {
      error_message = BaseMessages.getString( PKG, "FileExistsMeta.CheckResult.FileFieldOK" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_OK, error_message, stepMeta );
      remarks.add( cr );
    }
    // See if we have input streams leading to this step!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( CheckResult.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "FileExistsMeta.CheckResult.ReceivingInfoFromOtherSteps" ), stepMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "FileExistsMeta.CheckResult.NoInpuReceived" ), stepMeta );
      remarks.add( cr );
    }

  }

  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr,
    TransMeta transMeta, Trans trans ) {
    return new FileExists( stepMeta, stepDataInterface, cnr, transMeta, trans );
  }

  public StepDataInterface getStepData() {
    return new FileExistsData();
  }

  public boolean supportsErrorHandling() {
    return true;
  }

}
