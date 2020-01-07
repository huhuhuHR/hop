/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.trans.steps.userdefinedjavaclass;

import com.google.common.annotations.VisibleForTesting;
import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.Scanner;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopStepException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.injection.Injection;
import org.apache.hop.core.injection.InjectionDeep;
import org.apache.hop.core.injection.InjectionSupported;
import org.apache.hop.core.injection.NullNumberConverter;
import org.apache.hop.core.logging.LogChannelInterface;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;

import org.apache.hop.trans.Trans;
import org.apache.hop.trans.TransMeta;
import org.apache.hop.trans.step.BaseStepMeta;
import org.apache.hop.trans.step.StepDataInterface;
import org.apache.hop.trans.step.StepIOMetaInterface;
import org.apache.hop.trans.step.StepInterface;
import org.apache.hop.trans.step.StepMeta;
import org.apache.hop.trans.step.StepMetaInterface;
import org.apache.hop.trans.steps.fieldsplitter.DataTypeConverter;
import org.apache.hop.trans.steps.userdefinedjavaclass.UserDefinedJavaClassDef.ClassType;
import org.apache.hop.metastore.api.IMetaStore;
import org.w3c.dom.Node;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@InjectionSupported( localizationPrefix = "UserDefinedJavaClass.Injection.", groups = {
  "PARAMETERS", "TARGET_STEPS", "INFO_STEPS", "JAVA_CLASSES", "FIELD_INFO" } )
public class UserDefinedJavaClassMeta extends BaseStepMeta implements StepMetaInterface {
  private static Class<?> PKG = UserDefinedJavaClassMeta.class; // for i18n purposes, needed by Translator2!!

  public enum ElementNames {
    class_type, class_name, class_source, definitions, definition, fields, field, field_name, field_type,
      field_length, field_precision, clear_result_fields,

      info_steps, info_step, info_, target_steps, target_step, target_,

      step_tag, step_name, step_description,

      usage_parameters, usage_parameter, parameter_tag, parameter_value, parameter_description,
  }

  @InjectionDeep
  private List<FieldInfo> fields = new ArrayList<FieldInfo>();

  @InjectionDeep
  private List<UserDefinedJavaClassDef> definitions = new ArrayList<UserDefinedJavaClassDef>();
  public Class<TransformClassBase> cookedTransformClass;
  public List<Exception> cookErrors = new ArrayList<Exception>( 0 );
  private static final Cache<String, Class<?>> classCache;

  @Injection( name = "CLEAR_RESULT_FIELDS" )
  private boolean clearingResultFields;

  private boolean changed;

  @InjectionDeep
  private List<InfoStepDefinition> infoStepDefinitions;
  @InjectionDeep
  private List<TargetStepDefinition> targetStepDefinitions;
  @InjectionDeep
  private List<UsageParameter> usageParameters;

  static {
    VariableSpace vs = new Variables();
    vs.initializeVariablesFrom( null ); // sets up the default variables
    String maxSizeStr = vs.getVariable( UserDefinedJavaClass.HOP_DEFAULT_CLASS_CACHE_SIZE, "100" );
    int maxCacheSize = -1;
    try {
      maxCacheSize = Integer.parseInt( maxSizeStr );
    } catch ( Exception ignored ) {
      maxCacheSize = 100; // default to 100 if property not set
    }
    // Initialize Class Cache
    classCache = CacheBuilder.newBuilder().maximumSize( maxCacheSize ).build();
  }

  public static class FieldInfo implements Cloneable {
    @Injection( name = "FIELD_NAME", group = "FIELD_INFO" )
    public final String name;
    @Injection( name = "FIELD_TYPE", group = "FIELD_INFO", convertEmpty = true, converter = DataTypeConverter.class )
    public final int type;
    @Injection( name = "FIELD_LENGTH", group = "FIELD_INFO", convertEmpty = true, converter = NullNumberConverter.class )
    public final int length;
    @Injection( name = "FIELD_PRECISION", group = "FIELD_INFO", convertEmpty = true, converter = NullNumberConverter.class )
    public final int precision;

    public FieldInfo() {
      this( null, ValueMetaInterface.TYPE_STRING, -1, -1 );
    }

    public FieldInfo( String name, int type, int length, int precision ) {
      super();
      this.name = name;
      this.type = type;
      this.length = length;
      this.precision = precision;
    }

    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }

  public UserDefinedJavaClassMeta() {
    super();
    changed = true;
    infoStepDefinitions = new ArrayList<>();
    targetStepDefinitions = new ArrayList<>();
    usageParameters = new ArrayList<>();
  }

  @VisibleForTesting
  Class<?> cookClass( UserDefinedJavaClassDef def, ClassLoader clsloader ) throws CompileException, IOException, RuntimeException, HopStepException {

    String checksum = def.getChecksum();
    Class<?> rtn = UserDefinedJavaClassMeta.classCache.getIfPresent( checksum );
    if ( rtn != null ) {
      return rtn;
    }

    if ( Thread.currentThread().getContextClassLoader() == null ) {
      Thread.currentThread().setContextClassLoader( this.getClass().getClassLoader() );
    }

    ClassBodyEvaluator cbe = new ClassBodyEvaluator();
    if ( clsloader == null ) {
      cbe.setParentClassLoader( Thread.currentThread().getContextClassLoader() );
    } else {
      cbe.setParentClassLoader( clsloader );
    }

    cbe.setClassName( def.getClassName() );

    StringReader sr;
    if ( def.isTransformClass() ) {
      cbe.setExtendedType( TransformClassBase.class );
      sr = new StringReader( def.getTransformedSource() );
    } else {
      sr = new StringReader( def.getSource() );
    }

    cbe.setDefaultImports( new String[] {
      "org.apache.hop.trans.steps.userdefinedjavaclass.*", "org.apache.hop.trans.step.*",
      "org.apache.hop.core.row.*", "org.apache.hop.core.*", "org.apache.hop.core.exception.*" } );

    cbe.cook( new Scanner( null, sr ) );
    rtn = cbe.getClazz();
    UserDefinedJavaClassMeta.classCache.put( checksum, rtn );
    return rtn;
  }

  @SuppressWarnings( "unchecked" )
  public void cookClasses() {
    cookErrors.clear();
    ClassLoader clsloader = null;
    for ( UserDefinedJavaClassDef def : getDefinitions() ) {
      if ( def.isActive() ) {
        try {
          Class<?> cookedClass = cookClass( def, clsloader );
          clsloader = cookedClass.getClassLoader();
          if ( def.isTransformClass() ) {
            cookedTransformClass = (Class<TransformClassBase>) cookedClass;
          }

        } catch ( Throwable e ) {
          CompileException exception = new CompileException( e.getMessage(), null );
          exception.setStackTrace( new StackTraceElement[] {} );
          cookErrors.add( exception );
        }
      }
    }
    changed = false;
  }

  public TransformClassBase newChildInstance( UserDefinedJavaClass parent, UserDefinedJavaClassMeta meta, UserDefinedJavaClassData data ) {
    if ( !checkClassCookings( getLog() ) ) {
      return null;
    }

    try {
      return cookedTransformClass.getConstructor( UserDefinedJavaClass.class, UserDefinedJavaClassMeta.class, UserDefinedJavaClassData.class ).newInstance( parent, meta, data );
    } catch ( Exception e ) {
      if ( log.isDebug() ) {
        log.logError( "Full debugging stacktrace of UserDefinedJavaClass instanciation exception:", e.getCause() );
      }
      HopException kettleException = new HopException( e.getMessage() );
      kettleException.setStackTrace( new StackTraceElement[] {} );
      cookErrors.add( kettleException );
      return null;
    }
  }

  public List<FieldInfo> getFieldInfo() {
    return Collections.unmodifiableList( fields );
  }

  public void setFieldInfo( List<FieldInfo> fields ) {
    replaceFields( fields );
  }

  public void replaceFields( List<FieldInfo> fields ) {
    this.fields = fields;
    changed = true;
  }

  public List<UserDefinedJavaClassDef> getDefinitions() {
    return Collections.unmodifiableList( definitions );
  }

  /**
   * This method oders the classes by sorting all the normal classes by alphabetic order and then sorting
   * all the transaction classes by alphabetical order. This makes the resolution of classes deterministic by type and
   * then by class name.
   * @param definitions - Unorder list of user defined classes
   * @return - Ordered list of user defined classes
   */
  @VisibleForTesting
  protected List<UserDefinedJavaClassDef> orderDefinitions( List<UserDefinedJavaClassDef> definitions ) {
    List<UserDefinedJavaClassDef> orderedDefinitions = new ArrayList<>( definitions.size() );
    List<UserDefinedJavaClassDef> transactions =
      definitions.stream()
        .filter( def -> def.isTransformClass() && def.isActive() )
        .sorted( ( p1, p2 ) -> p1.getClassName().compareTo( p2.getClassName() ) )
        .collect( Collectors.toList() );

    List<UserDefinedJavaClassDef> normalClasses =
      definitions.stream()
        .filter( def -> !def.isTransformClass() )
        .sorted( ( p1, p2 ) -> p1.getClassName().compareTo( p2.getClassName() ) )
        .collect( Collectors.toList() );

    orderedDefinitions.addAll( normalClasses );
    orderedDefinitions.addAll( transactions );
    return orderedDefinitions;
  }

  public void replaceDefinitions( List<UserDefinedJavaClassDef> definitions ) {
    this.definitions.clear();
    this.definitions = orderDefinitions( definitions );
    changed = true;
  }

  public void loadXML( Node stepnode, IMetaStore metaStore ) throws HopXMLException {
    readData( stepnode );
  }

  public Object clone() {
    try {

      UserDefinedJavaClassMeta retval = (UserDefinedJavaClassMeta) super.clone();

      if ( fields != null ) {
        List<FieldInfo> newFields = new ArrayList<FieldInfo>( fields.size() );
        for ( FieldInfo field : fields ) {
          newFields.add( (FieldInfo) field.clone() );
        }
        retval.fields = newFields;
      }

      if ( definitions != null ) {
        List<UserDefinedJavaClassDef> newDefinitions = new ArrayList<UserDefinedJavaClassDef>();
        for ( UserDefinedJavaClassDef def : definitions ) {
          newDefinitions.add( (UserDefinedJavaClassDef) def.clone() );
        }
        retval.definitions = newDefinitions;
      }

      retval.cookedTransformClass = null;
      retval.cookErrors = new ArrayList<Exception>( 0 );

      if ( infoStepDefinitions != null ) {
        List<InfoStepDefinition> newInfoStepDefinitions = new ArrayList<>();
        for ( InfoStepDefinition step : infoStepDefinitions ) {
          newInfoStepDefinitions.add( (InfoStepDefinition) step.clone() );
        }
        retval.infoStepDefinitions = newInfoStepDefinitions;
      }

      if ( targetStepDefinitions != null ) {
        List<TargetStepDefinition> newTargetStepDefinitions = new ArrayList<>();
        for ( TargetStepDefinition step : targetStepDefinitions ) {
          newTargetStepDefinitions.add( (TargetStepDefinition) step.clone() );
        }
        retval.targetStepDefinitions = newTargetStepDefinitions;
      }

      if ( usageParameters != null ) {
        List<UsageParameter> newUsageParameters = new ArrayList<UsageParameter>();
        for ( UsageParameter param : usageParameters ) {
          newUsageParameters.add( (UsageParameter) param.clone() );
        }
        retval.usageParameters = newUsageParameters;
      }

      return retval;

    } catch ( CloneNotSupportedException e ) {
      return null;
    }
  }

  private void readData( Node stepnode ) throws HopXMLException {
    try {
      Node definitionsNode = XMLHandler.getSubNode( stepnode, ElementNames.definitions.name() );
      int nrDefinitions = XMLHandler.countNodes( definitionsNode, ElementNames.definition.name() );

      for ( int i = 0; i < nrDefinitions; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( definitionsNode, ElementNames.definition.name(), i );
        definitions.add( new UserDefinedJavaClassDef(
          ClassType.valueOf( XMLHandler.getTagValue( fnode, ElementNames.class_type.name() ) ),
          XMLHandler.getTagValue( fnode, ElementNames.class_name.name() ),
          XMLHandler.getTagValue( fnode, ElementNames.class_source.name() ) ) );
      }
      definitions = orderDefinitions( definitions );

      Node fieldsNode = XMLHandler.getSubNode( stepnode, ElementNames.fields.name() );
      int nrfields = XMLHandler.countNodes( fieldsNode, ElementNames.field.name() );

      for ( int i = 0; i < nrfields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fieldsNode, ElementNames.field.name(), i );
        fields.add( new FieldInfo(
          XMLHandler.getTagValue( fnode, ElementNames.field_name.name() ),
          ValueMetaFactory.getIdForValueMeta( XMLHandler.getTagValue( fnode, ElementNames.field_type.name() ) ),
          Const.toInt( XMLHandler.getTagValue( fnode, ElementNames.field_length.name() ), -1 ),
          Const.toInt( XMLHandler.getTagValue( fnode, ElementNames.field_precision.name() ), -1 ) ) );
      }

      setClearingResultFields( !"N".equals( XMLHandler.getTagValue( stepnode, ElementNames.clear_result_fields
        .name() ) ) );

      infoStepDefinitions.clear();
      Node infosNode = XMLHandler.getSubNode( stepnode, ElementNames.info_steps.name() );
      int nrInfos = XMLHandler.countNodes( infosNode, ElementNames.info_step.name() );
      for ( int i = 0; i < nrInfos; i++ ) {
        Node infoNode = XMLHandler.getSubNodeByNr( infosNode, ElementNames.info_step.name(), i );
        InfoStepDefinition stepDefinition = new InfoStepDefinition();
        stepDefinition.tag = XMLHandler.getTagValue( infoNode, ElementNames.step_tag.name() );
        stepDefinition.stepName = XMLHandler.getTagValue( infoNode, ElementNames.step_name.name() );
        stepDefinition.description = XMLHandler.getTagValue( infoNode, ElementNames.step_description.name() );
        infoStepDefinitions.add( stepDefinition );
      }

      targetStepDefinitions.clear();
      Node targetsNode = XMLHandler.getSubNode( stepnode, ElementNames.target_steps.name() );
      int nrTargets = XMLHandler.countNodes( targetsNode, ElementNames.target_step.name() );
      for ( int i = 0; i < nrTargets; i++ ) {
        Node targetNode = XMLHandler.getSubNodeByNr( targetsNode, ElementNames.target_step.name(), i );
        TargetStepDefinition stepDefinition = new TargetStepDefinition();
        stepDefinition.tag = XMLHandler.getTagValue( targetNode, ElementNames.step_tag.name() );
        stepDefinition.stepName = XMLHandler.getTagValue( targetNode, ElementNames.step_name.name() );
        stepDefinition.description = XMLHandler.getTagValue( targetNode, ElementNames.step_description.name() );
        targetStepDefinitions.add( stepDefinition );
      }

      usageParameters.clear();
      Node parametersNode = XMLHandler.getSubNode( stepnode, ElementNames.usage_parameters.name() );
      int nrParameters = XMLHandler.countNodes( parametersNode, ElementNames.usage_parameter.name() );
      for ( int i = 0; i < nrParameters; i++ ) {
        Node parameterNode = XMLHandler.getSubNodeByNr( parametersNode, ElementNames.usage_parameter.name(), i );
        UsageParameter usageParameter = new UsageParameter();
        usageParameter.tag = XMLHandler.getTagValue( parameterNode, ElementNames.parameter_tag.name() );
        usageParameter.value = XMLHandler.getTagValue( parameterNode, ElementNames.parameter_value.name() );
        usageParameter.description =
          XMLHandler.getTagValue( parameterNode, ElementNames.parameter_description.name() );
        usageParameters.add( usageParameter );
      }
    } catch ( Exception e ) {
      throw new HopXMLException( BaseMessages.getString(
        PKG, "UserDefinedJavaClassMeta.Exception.UnableToLoadStepInfoFromXML" ), e );
    }
  }

  public void setDefault() {
    // Moved the default code generation out of Meta since the Snippits class is in the UI package which isn't in the
    // classpath.
  }

  private boolean checkClassCookings( LogChannelInterface logChannel ) {
    boolean ok = cookedTransformClass != null && cookErrors.size() == 0;
    if ( changed ) {
      cookClasses();
      if ( cookedTransformClass == null ) {
        if ( cookErrors.size() > 0 ) {
          logChannel.logDebug( BaseMessages.getString(
            PKG, "UserDefinedJavaClass.Exception.CookingError", cookErrors.get( 0 ) ) );
        }
        ok = false;
      } else {
        ok = true;
      }
    }
    return ok;
  }

  @Override
  public StepIOMetaInterface getStepIOMeta() {
    if ( !checkClassCookings( getLog() ) ) {
      return super.getStepIOMeta();
    }

    try {
      Method getStepIOMeta = cookedTransformClass.getMethod( "getStepIOMeta", UserDefinedJavaClassMeta.class );
      if ( getStepIOMeta != null ) {
        StepIOMetaInterface stepIoMeta = (StepIOMetaInterface) getStepIOMeta.invoke( null, this );
        if ( stepIoMeta == null ) {
          return super.getStepIOMeta();
        } else {
          return stepIoMeta;
        }
      } else {
        return super.getStepIOMeta();
      }
    } catch ( Exception e ) {
      e.printStackTrace();
      return super.getStepIOMeta();
    }
  }

  @Override
  public void searchInfoAndTargetSteps( List<StepMeta> steps ) {
    for ( InfoStepDefinition stepDefinition : infoStepDefinitions ) {
      stepDefinition.stepMeta = StepMeta.findStep( steps, stepDefinition.stepName );
    }
    for ( TargetStepDefinition stepDefinition : targetStepDefinitions ) {
      stepDefinition.stepMeta = StepMeta.findStep( steps, stepDefinition.stepName );
    }
  }

  public void getFields( RowMetaInterface row, String originStepname, RowMetaInterface[] info, StepMeta nextStep,
    VariableSpace space, IMetaStore metaStore ) throws HopStepException {
    if ( !checkClassCookings( getLog() ) ) {
      if ( cookErrors.size() > 0 ) {
        throw new HopStepException( "Error initializing UserDefinedJavaClass to get fields: ", cookErrors
          .get( 0 ) );
      } else {
        return;
      }
    }

    try {
      Method getFieldsMethod =
        cookedTransformClass.getMethod(
          "getFields", boolean.class, RowMetaInterface.class, String.class, RowMetaInterface[].class,
          StepMeta.class, VariableSpace.class, List.class );
      getFieldsMethod.invoke(
        null, isClearingResultFields(), row, originStepname, info, nextStep, space, getFieldInfo() );
    } catch ( Exception e ) {
      throw new HopStepException( "Error executing UserDefinedJavaClass.getFields(): ", e );
    }
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder( 300 );

    retval.append( String.format( "\n    <%s>", ElementNames.definitions.name() ) );
    for ( UserDefinedJavaClassDef def : definitions ) {
      retval.append( String.format( "\n        <%s>", ElementNames.definition.name() ) );
      retval.append( "\n        " ).append(
        XMLHandler.addTagValue( ElementNames.class_type.name(), def.getClassType().name() ) );
      retval.append( "\n        " ).append(
        XMLHandler.addTagValue( ElementNames.class_name.name(), def.getClassName() ) );
      retval.append( "\n        " );
      retval.append( XMLHandler.addTagValue( ElementNames.class_source.name(), def.getSource() ) );
      retval.append( String.format( "\n        </%s>", ElementNames.definition.name() ) );
    }
    retval.append( String.format( "\n    </%s>", ElementNames.definitions.name() ) );

    retval.append( String.format( "\n    <%s>", ElementNames.fields.name() ) );
    for ( FieldInfo fi : fields ) {
      retval.append( String.format( "\n        <%s>", ElementNames.field.name() ) );
      retval.append( "\n        " ).append( XMLHandler.addTagValue( ElementNames.field_name.name(), fi.name ) );
      retval.append( "\n        " ).append(
        XMLHandler.addTagValue( ElementNames.field_type.name(), ValueMetaFactory.getValueMetaName( fi.type ) ) );
      retval.append( "\n        " ).append( XMLHandler.addTagValue( ElementNames.field_length.name(), fi.length ) );
      retval.append( "\n        " ).append(
        XMLHandler.addTagValue( ElementNames.field_precision.name(), fi.precision ) );
      retval.append( String.format( "\n        </%s>", ElementNames.field.name() ) );
    }
    retval.append( String.format( "\n    </%s>", ElementNames.fields.name() ) );
    retval.append( XMLHandler.addTagValue( ElementNames.clear_result_fields.name(), clearingResultFields ) );

    // Add the XML for the info step definitions...
    //
    retval.append( XMLHandler.openTag( ElementNames.info_steps.name() ) );
    for ( InfoStepDefinition stepDefinition : infoStepDefinitions ) {
      retval.append( XMLHandler.openTag( ElementNames.info_step.name() ) );
      retval.append( XMLHandler.addTagValue( ElementNames.step_tag.name(), stepDefinition.tag ) );
      retval.append( XMLHandler.addTagValue( ElementNames.step_name.name(), stepDefinition.stepMeta != null
        ? stepDefinition.stepMeta.getName() : null ) );
      retval.append( XMLHandler.addTagValue( ElementNames.step_description.name(), stepDefinition.description ) );
      retval.append( XMLHandler.closeTag( ElementNames.info_step.name() ) );
    }
    retval.append( XMLHandler.closeTag( ElementNames.info_steps.name() ) );

    // Add the XML for the target step definitions...
    //
    retval.append( XMLHandler.openTag( ElementNames.target_steps.name() ) );
    for ( TargetStepDefinition stepDefinition : targetStepDefinitions ) {
      retval.append( XMLHandler.openTag( ElementNames.target_step.name() ) );
      retval.append( XMLHandler.addTagValue( ElementNames.step_tag.name(), stepDefinition.tag ) );
      retval.append( XMLHandler.addTagValue( ElementNames.step_name.name(), stepDefinition.stepMeta != null
        ? stepDefinition.stepMeta.getName() : null ) );
      retval.append( XMLHandler.addTagValue( ElementNames.step_description.name(), stepDefinition.description ) );
      retval.append( XMLHandler.closeTag( ElementNames.target_step.name() ) );
    }
    retval.append( XMLHandler.closeTag( ElementNames.target_steps.name() ) );

    retval.append( XMLHandler.openTag( ElementNames.usage_parameters.name() ) );
    for ( UsageParameter usageParameter : usageParameters ) {
      retval.append( XMLHandler.openTag( ElementNames.usage_parameter.name() ) );
      retval.append( XMLHandler.addTagValue( ElementNames.parameter_tag.name(), usageParameter.tag ) );
      retval.append( XMLHandler.addTagValue( ElementNames.parameter_value.name(), usageParameter.value ) );
      retval.append( XMLHandler
        .addTagValue( ElementNames.parameter_description.name(), usageParameter.description ) );
      retval.append( XMLHandler.closeTag( ElementNames.usage_parameter.name() ) );
    }
    retval.append( XMLHandler.closeTag( ElementNames.usage_parameters.name() ) );

    return retval.toString();
  }

  public void check( List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepinfo,
    RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
    IMetaStore metaStore ) {
    CheckResult cr;

    // See if we have input streams leading to this step!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "UserDefinedJavaClassMeta.CheckResult.ConnectedStepOK2" ), stepinfo );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "UserDefinedJavaClassMeta.CheckResult.NoInputReceived" ), stepinfo );
      remarks.add( cr );
    }
  }

  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr,
    TransMeta transMeta, Trans trans ) {
    UserDefinedJavaClass userDefinedJavaClass =
      new UserDefinedJavaClass( stepMeta, stepDataInterface, cnr, transMeta, trans );
    if ( trans.hasHaltedSteps() ) {
      return null;
    }

    return userDefinedJavaClass;
  }

  public StepDataInterface getStepData() {
    return new UserDefinedJavaClassData();
  }

  public boolean supportsErrorHandling() {
    return true;
  }

  /**
   * @return the clearingResultFields
   */
  public boolean isClearingResultFields() {
    return clearingResultFields;
  }

  /**
   * @param clearingResultFields
   *          the clearingResultFields to set
   */
  public void setClearingResultFields( boolean clearingResultFields ) {
    this.clearingResultFields = clearingResultFields;
  }

  /**
   * @return the infoStepDefinitions
   */
  public List<InfoStepDefinition> getInfoStepDefinitions() {
    return infoStepDefinitions;
  }

  /**
   * @param infoStepDefinitions
   *          the infoStepDefinitions to set
   */
  public void setInfoStepDefinitions( List<InfoStepDefinition> infoStepDefinitions ) {
    this.infoStepDefinitions = infoStepDefinitions;
  }

  /**
   * @return the targetStepDefinitions
   */
  public List<TargetStepDefinition> getTargetStepDefinitions() {
    return targetStepDefinitions;
  }

  /**
   * @param targetStepDefinitions
   *          the targetStepDefinitions to set
   */
  public void setTargetStepDefinitions( List<TargetStepDefinition> targetStepDefinitions ) {
    this.targetStepDefinitions = targetStepDefinitions;
  }

  @Override
  public boolean excludeFromRowLayoutVerification() {
    return true;
  }

  /**
   * @return the usageParameters
   */
  public List<UsageParameter> getUsageParameters() {
    return usageParameters;
  }

  /**
   * @param usageParameters
   *          the usageParameters to set
   */
  public void setUsageParameters( List<UsageParameter> usageParameters ) {
    this.usageParameters = usageParameters;
  }
}
