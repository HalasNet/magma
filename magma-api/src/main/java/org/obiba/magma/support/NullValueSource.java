/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.support;

import java.util.Collections;
import java.util.SortedSet;

import javax.validation.constraints.NotNull;

import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueSource;
import org.obiba.magma.ValueType;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.VectorSource;

public final class NullValueSource implements ValueSource, VectorSource {

  private final ValueType valueType;

  public NullValueSource(ValueType valueType) {
    this.valueType = valueType;
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return valueType;
  }

  @NotNull
  @Override
  public Value getValue(ValueSet valueSet) {
    return valueType.nullValue();
  }

  @Override
  public boolean supportVectorSource() {
    return true;
  }

  @NotNull
  @Override
  public VectorSource asVectorSource() {
    return this;
  }

  @Override
  public Iterable<Value> getValues(SortedSet<VariableEntity> entities) {
    return Collections.nCopies(entities.size(), valueType.nullValue());
  }

}