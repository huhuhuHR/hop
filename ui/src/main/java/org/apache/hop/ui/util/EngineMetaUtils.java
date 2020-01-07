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

package org.apache.hop.ui.util;

import org.apache.hop.core.EngineMetaInterface;
import org.apache.hop.job.JobMeta;
import org.apache.hop.trans.TransMeta;

public class EngineMetaUtils {

  /**
   * Validates if {@code engineMetaInterface} is Job or Transformation.
   * 
   * @param engineMetaInterface
   * @return true if engineMetaInterface instance is Job or Transformation, otherwise false.
   */
  public static boolean isJobOrTransformation( EngineMetaInterface engineMetaInterface ) {

    return ( engineMetaInterface instanceof TransMeta ) || (engineMetaInterface instanceof JobMeta );
  }

}
