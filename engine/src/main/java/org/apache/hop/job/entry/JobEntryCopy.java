/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2019 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.job.entry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.hop.base.BaseMeta;
import org.apache.hop.cluster.SlaveServer;
import org.apache.hop.core.AttributesInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.attributes.AttributesUtil;
import org.apache.hop.core.changed.ChangedFlagInterface;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.gui.GUIPositionInterface;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.plugins.JobEntryPluginType;
import org.apache.hop.core.plugins.PluginInterface;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.core.xml.XMLInterface;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.job.JobMeta;
import org.apache.hop.job.entries.missing.MissingEntry;
import org.apache.hop.metastore.api.IMetaStore;
import org.w3c.dom.Node;

/**
 * This class describes the fact that a single JobEntry can be used multiple times in the same Job. Therefore it contains
 * a link to a JobEntry, a position, a number, etc.
 *
 * @author Matt
 * @since 01-10-2003
 *
 */

public class JobEntryCopy implements Cloneable, XMLInterface, GUIPositionInterface, ChangedFlagInterface,
  AttributesInterface, BaseMeta {
  private static final String XML_TAG = "entry";

  private static final String XML_ATTRIBUTE_JOB_ENTRY_COPY = AttributesUtil.XML_TAG + "_kjc";

  private JobEntryInterface entry;

  private String suggestion = "";

  private int nr; // Copy nr. 0 is the base copy...

  private boolean selected;

  private boolean isDeprecated;

  private Point location;

  /**
   * Flag to indicate that the job entries following this one are launched in parallel
   */
  private boolean launchingInParallel;

  private boolean draw;

  private JobMeta parentJobMeta;

  private Map<String, Map<String, String>> attributesMap;

  public JobEntryCopy() {
    clear();
  }

  public JobEntryCopy( JobEntryInterface entry ) {
    this();
    setEntry( entry );
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder( 100 );

    retval.append( "    " ).append( XMLHandler.openTag( XML_TAG ) ).append( Const.CR );
    entry.setParentJobMeta( parentJobMeta );  // Attempt to set the JobMeta for entries that need it
    retval.append( entry.getXML() );

    retval.append( "      " ).append( XMLHandler.addTagValue( "parallel", launchingInParallel ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "draw", draw ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "nr", nr ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "xloc", location.x ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "yloc", location.y ) );

    retval.append( AttributesUtil.getAttributesXml( attributesMap, XML_ATTRIBUTE_JOB_ENTRY_COPY ) );

    retval.append( "    " ).append( XMLHandler.closeTag( XML_TAG ) ).append( Const.CR );
    return retval.toString();
  }


  public JobEntryCopy( Node entrynode, List<SlaveServer> slaveServers, IMetaStore metaStore ) throws HopXMLException {
    try {
      String stype = XMLHandler.getTagValue( entrynode, "type" );
      PluginRegistry registry = PluginRegistry.getInstance();
      PluginInterface jobPlugin = registry.findPluginWithId( JobEntryPluginType.class, stype, true );
      if ( jobPlugin == null ) {
        String name = XMLHandler.getTagValue( entrynode, "name" );
        entry = new MissingEntry( name, stype );
      } else {
        entry = registry.loadClass( jobPlugin, JobEntryInterface.class );
      }
      // Get an empty JobEntry of the appropriate class...
      if ( entry != null ) {
        if ( jobPlugin != null ) {
          entry.setPluginId( jobPlugin.getIds()[0] );
        }
        entry.setMetaStore( metaStore ); // inject metastore
        entry.loadXML( entrynode, slaveServers, metaStore );

        // Handle GUI information: nr & location?
        setNr( Const.toInt( XMLHandler.getTagValue( entrynode, "nr" ), 0 ) );
        setLaunchingInParallel( "Y".equalsIgnoreCase( XMLHandler.getTagValue( entrynode, "parallel" ) ) );
        setDrawn( "Y".equalsIgnoreCase( XMLHandler.getTagValue( entrynode, "draw" ) ) );
        int x = Const.toInt( XMLHandler.getTagValue( entrynode, "xloc" ), 0 );
        int y = Const.toInt( XMLHandler.getTagValue( entrynode, "yloc" ), 0 );
        setLocation( x, y );

        Node jobEntryCopyAttributesNode = XMLHandler.getSubNode( entrynode, XML_ATTRIBUTE_JOB_ENTRY_COPY );
        if ( jobEntryCopyAttributesNode != null ) {
          attributesMap = AttributesUtil.loadAttributes( jobEntryCopyAttributesNode );
        } else {
          // [PDI-17345] If the appropriate attributes node wasn't found, this must be an old file (prior to this fix).
          // Before this fix it was very probable to exist two attributes groups. While this is not very valid, in some
          // scenarios the Job worked as expected; so by trying to load the LAST one into the JobEntryCopy, we
          // simulate that behaviour.
          attributesMap =
            AttributesUtil.loadAttributes( XMLHandler.getLastSubNode( entrynode, AttributesUtil.XML_TAG ) );
        }

        setDeprecationAndSuggestedJobEntry();
      }
    } catch ( Throwable e ) {
      String message = "Unable to read Job Entry copy info from XML node : " + e.toString();
      throw new HopXMLException( message, e );
    }
  }

  public void clear() {
    location = null;
    entry = null;
    nr = 0;
    launchingInParallel = false;
    attributesMap = new HashMap<>();
  }

  public Object clone() {
    JobEntryCopy ge = new JobEntryCopy();
    ge.replaceMeta( this );

    for ( final Map.Entry<String, Map<String, String>> attribute : attributesMap.entrySet() ) {
      ge.attributesMap.put( attribute.getKey(), attribute.getValue() );
    }

    return ge;
  }

  public void replaceMeta( JobEntryCopy jobEntryCopy ) {
    entry = (JobEntryInterface) jobEntryCopy.entry.clone();
    nr = jobEntryCopy.nr; // Copy nr. 0 is the base copy...

    selected = jobEntryCopy.selected;
    if ( jobEntryCopy.location != null ) {
      location = new Point( jobEntryCopy.location.x, jobEntryCopy.location.y );
    }
    launchingInParallel = jobEntryCopy.launchingInParallel;
    draw = jobEntryCopy.draw;

    setChanged();
  }

  public Object clone_deep() {
    JobEntryCopy ge = (JobEntryCopy) clone();

    // Copy underlying object as well...
    ge.entry = (JobEntryInterface) entry.clone();

    return ge;
  }

  public boolean equals( Object o ) {
    if ( o == null ) {
      return false;
    }
    JobEntryCopy je = (JobEntryCopy) o;
    return je.entry.getName().equalsIgnoreCase( entry.getName() ) && je.getNr() == getNr();
  }

  @Override
  public int hashCode() {
    return entry.getName().hashCode() ^ Integer.valueOf( getNr() ).hashCode();
  }

  public void setEntry( JobEntryInterface je ) {
    entry = je;
    if ( entry != null ) {
      if ( entry.getPluginId() == null ) {
        entry.setPluginId( PluginRegistry.getInstance().getPluginId( JobEntryPluginType.class, entry ) );
      }
      setDeprecationAndSuggestedJobEntry();
    }
  }

  public JobEntryInterface getEntry() {
    return entry;
  }

  /**
   * @return entry in JobEntryInterface.typeCode[] for native jobs, entry.getTypeCode() for plugins
   */
  public String getTypeDesc() {
    PluginInterface plugin =
      PluginRegistry.getInstance().findPluginWithId( JobEntryPluginType.class, entry.getPluginId() );
    return plugin.getDescription();
  }

  public void setLocation( int x, int y ) {
    int nx = ( x >= 0 ? x : 0 );
    int ny = ( y >= 0 ? y : 0 );

    Point loc = new Point( nx, ny );
    if ( !loc.equals( location ) ) {
      setChanged();
    }
    location = loc;
  }

  public void setLocation( Point loc ) {
    if ( loc != null && !loc.equals( location ) ) {
      setChanged();
    }
    location = loc;
  }

  public Point getLocation() {
    return location;
  }

  public void setChanged() {
    setChanged( true );
  }

  public void setChanged( boolean ch ) {
    entry.setChanged( ch );
  }

  public void clearChanged() {
    entry.setChanged( false );
  }

  public boolean hasChanged() {
    return entry.hasChanged();
  }

  public int getNr() {
    return nr;
  }

  public void setNr( int n ) {
    nr = n;
  }

  public void setLaunchingInParallel( boolean p ) {
    launchingInParallel = p;
  }

  public boolean isDrawn() {
    return draw;
  }

  public void setDrawn() {
    setDrawn( true );
  }

  public void setDrawn( boolean d ) {
    draw = d;
  }

  public boolean isLaunchingInParallel() {
    return launchingInParallel;
  }

  public void setSelected( boolean sel ) {
    selected = sel;
  }

  public void flipSelected() {
    selected = !selected;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setDescription( String description ) {
    entry.setDescription( description );
  }

  public String getDescription() {
    return entry.getDescription();
  }

  public boolean isStart() {
    return entry.isStart();
  }

  public boolean isDummy() {
    return entry.isDummy();
  }

  public boolean isMissing() {
    return entry instanceof MissingEntry;
  }

  public boolean isTransformation() {
    return entry.isTransformation();
  }

  public boolean isJob() {
    return entry.isJob();
  }

  public boolean evaluates() {
    if ( entry != null ) {
      return entry.evaluates();
    }
    return false;
  }

  public boolean isUnconditional() {
    if ( entry != null ) {
      return entry.isUnconditional();
    }
    return true;
  }

  public boolean isEvaluation() {
    return entry.isEvaluation();
  }

  public boolean isMail() {
    return entry.isMail();
  }

  public boolean isSpecial() {
    return entry.isSpecial();
  }

  public String toString() {
    if ( entry != null ) {
      return entry.getName() + "." + getNr();
    } else {
      return "null." + getNr();
    }
  }

  public String getName() {
    if ( entry != null ) {
      return entry.getName();
    } else {
      return "null";
    }
  }

  public void setName( String name ) {
    entry.setName( name );
  }

  public boolean resetErrorsBeforeExecution() {
    return entry.resetErrorsBeforeExecution();
  }

  public JobMeta getParentJobMeta() {
    return parentJobMeta;
  }

  public void setParentJobMeta( JobMeta parentJobMeta ) {
    this.parentJobMeta = parentJobMeta;
    this.entry.setParentJobMeta( parentJobMeta );
  }

  @Override
  public void setAttributesMap( Map<String, Map<String, String>> attributesMap ) {
    this.attributesMap = attributesMap;
  }

  @Override
  public Map<String, Map<String, String>> getAttributesMap() {
    return attributesMap;
  }

  @Override
  public void setAttribute( String groupName, String key, String value ) {
    Map<String, String> attributes = getAttributes( groupName );
    if ( attributes == null ) {
      attributes = new HashMap<>();
      attributesMap.put( groupName, attributes );
    }
    attributes.put( key, value );
  }

  @Override
  public void setAttributes( String groupName, Map<String, String> attributes ) {
    attributesMap.put( groupName, attributes );
  }

  @Override
  public Map<String, String> getAttributes( String groupName ) {
    return attributesMap.get( groupName );
  }

  @Override
  public String getAttribute( String groupName, String key ) {
    Map<String, String> attributes = attributesMap.get( groupName );
    if ( attributes == null ) {
      return null;
    }
    return attributes.get( key );
  }

  public boolean isDeprecated() {
    return isDeprecated;
  }

  public String getSuggestion() {
    return suggestion;
  }

  private void setDeprecationAndSuggestedJobEntry() {
    PluginRegistry registry = PluginRegistry.getInstance();
    final List<PluginInterface> deprecatedJobEntries = registry.getPluginsByCategory( JobEntryPluginType.class,
      BaseMessages.getString( JobMeta.class, "JobCategory.Category.Deprecated" ) );
    for ( PluginInterface p : deprecatedJobEntries ) {
      String[] ids = p.getIds();
      if ( !ArrayUtils.isEmpty( ids ) && ids[0].equals( this.entry != null ? this.entry.getPluginId() : "" ) ) {
        this.isDeprecated = true;
        this.suggestion = registry.findPluginWithId( JobEntryPluginType.class, this.entry.getPluginId() ) != null
          ? registry.findPluginWithId( JobEntryPluginType.class, this.entry.getPluginId() ).getSuggestion() : "";
        break;
      }
    }
  }
}
