/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.js.methods;

import com.google.common.base.Function;
import org.obiba.magma.Value;
import org.obiba.magma.ValueType;

/**
 * Function that transforms a value into another value.
 */
public interface ValueFunction extends Function<Value, Value> {

  /**
   * Get destination value type.
   *
   * @return
   */
  ValueType getValueType();

}
