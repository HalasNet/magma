/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.views;

import java.util.*;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.obiba.magma.Datasource;
import org.obiba.magma.Disposable;
import org.obiba.magma.Initialisable;
import org.obiba.magma.MagmaCacheExtension;
import org.obiba.magma.MagmaEngine;
import org.obiba.magma.NoSuchValueSetException;
import org.obiba.magma.NoSuchVariableException;
import org.obiba.magma.Timestamps;
import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueTable;
import org.obiba.magma.ValueType;
import org.obiba.magma.Variable;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.VariableValueSource;
import org.obiba.magma.VariableValueSourceWrapper;
import org.obiba.magma.VectorSource;
import org.obiba.magma.support.AbstractValueTableWrapper;
import org.obiba.magma.support.AbstractVariableValueSourceWrapper;
import org.obiba.magma.support.Disposables;
import org.obiba.magma.support.Initialisables;
import org.obiba.magma.support.VariableEntitiesCache;
import org.obiba.magma.transform.BijectiveFunction;
import org.obiba.magma.transform.BijectiveFunctions;
import org.obiba.magma.transform.TransformingValueTable;
import org.obiba.magma.type.DateTimeType;
import org.obiba.magma.views.support.AllClause;
import org.obiba.magma.views.support.NoneClause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@SuppressWarnings("OverlyCoupledClass")
public class View extends AbstractValueTableWrapper implements Initialisable, Disposable, TransformingValueTable {

  private static final Logger log = LoggerFactory.getLogger(View.class);

  private String name;

  @NotNull
  private ValueTable from;

  @NotNull
  private SelectClause select;

  @NotNull
  private WhereClause where;

  /**
   * A list of derived variables. Mutually exclusive with "select".
   */
  @NotNull
  private ListClause variables;

  private Value created;

  private Value updated;

  // need to be transient because of XML serialization of Views
  @Nullable
  @SuppressWarnings("TransientFieldInNonSerializableClass")
  private transient ViewAwareDatasource viewDatasource;

  @Nullable
  @SuppressWarnings("TransientFieldInNonSerializableClass")
  private transient VariableEntitiesCache variableEntitiesCache;

  /**
   * No-arg constructor for XStream.
   */
  @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
      justification = "Needed by XStream")
  public View() {
    setSelectClause(new AllClause());
    setWhereClause(new AllClause());
    setListClause(new NoneClause());
  }

  public View(String name, ValueTable... from) {
    this(name, new AllClause(), new AllClause(), null, from);
  }

  public View(String name, String[] innerFrom, ValueTable... from) {
    this(name, new AllClause(), new AllClause(), innerFrom, from);
  }

  @SuppressWarnings("ConstantConditions")
  @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
      justification = "Needed by XStream")
  public View(String name, @NotNull SelectClause selectClause, @NotNull WhereClause whereClause, String[] innerFrom,
      @NotNull ValueTable... from) {
    Preconditions.checkArgument(selectClause != null, "null selectClause");
    Preconditions.checkArgument(whereClause != null, "null whereClause");
    Preconditions.checkArgument(from != null && from.length > 0, "null or empty table list");
    this.name = name;
    this.from = from.length > 1 ?
        innerFrom != null ?
            new JoinTable(Arrays.<ValueTable>asList(from), Arrays.<String>asList(innerFrom))
            : new JoinTable(Arrays.<ValueTable>asList(from))
        : from[0];

    setSelectClause(selectClause);
    setWhereClause(whereClause);
    setListClause(new NoneClause());

    created = DateTimeType.get().now();
    updated = DateTimeType.get().now();
  }

  @Override
  public void initialise() {
    getListClause().setValueTable(this);
    Initialisables.initialise(getWrappedValueTable(), getSelectClause(), getWhereClause(), getListClause());
    if(isViewOfDerivedVariables()) {
      setSelectClause(new NoneClause());
    } else if(!(getSelectClause() instanceof NoneClause)) {
      setListClause(new NoneClause());
    } else {
      setListClause(new NoneClause());
      setSelectClause(new AllClause());
    }
  }

  @Override
  public void dispose() {
    Disposables.silentlyDispose(getWrappedValueTable(), getSelectClause(), getWhereClause(), getListClause());
  }

  public void setName(String name) {
    this.name = name;
  }

  @NotNull
  public SelectClause getSelectClause() {
    return select;
  }

  @NotNull
  public WhereClause getWhereClause() {
    return where;
  }

  @NotNull
  public ListClause getListClause() {
    return variables;
  }

  /**
   * Returns true is this is a {@link View} of derived variables, false if this is a {@code View} of selected (existing)
   * variables.
   */
  private boolean isViewOfDerivedVariables() {
    return !(getListClause() instanceof NoneClause);
  }

  @NotNull
  @Override
  public Datasource getDatasource() {
    return viewDatasource == null ? getWrappedValueTable().getDatasource() : viewDatasource;
  }

  @Override
  @NotNull
  public ValueTable getWrappedValueTable() {
    return from;
  }

  @NotNull
  @Override
  public Timestamps getTimestamps() {
    return new Timestamps() {

      @NotNull
      @Override
      public Value getLastUpdate() {
        if(updated == null || updated.isNull()) {
          return getWrappedValueTable().getTimestamps().getLastUpdate();
        }
        Value fromUpdate = getWrappedValueTable().getTimestamps().getLastUpdate();
        return !fromUpdate.isNull() && updated.compareTo(fromUpdate) < 0 ? fromUpdate : updated;
      }

      @NotNull
      @Override
      public Value getCreated() {
        return created == null || created.isNull() ? getWrappedValueTable().getTimestamps().getCreated() : created;
      }
    };
  }

  @SuppressWarnings({ "AssignmentToMethodParameter", "PMD.AvoidReassigningParameters" })
  public void setUpdated(@Nullable Value updated) {
    if(updated == null) updated = DateTimeType.get().nullValue();
    if(updated.getValueType() != DateTimeType.get()) throw new IllegalArgumentException();
    this.updated = updated;
  }

  @SuppressWarnings({ "AssignmentToMethodParameter", "PMD.AvoidReassigningParameters" })
  public void setCreated(@Nullable Value created) {
    if(created == null) created = DateTimeType.get().nullValue();
    if(created.getValueType() != DateTimeType.get()) throw new IllegalArgumentException();
    this.created = created;
  }

  @Override
  public boolean isView() {
    return true;
  }

  @Override
  public String getTableReference() {
    return (viewDatasource == null || getDatasource() == null ? "null" : getDatasource().getName()) + "." + getName();
  }

  @Override
  public int getVariableCount() {
    return Iterables.size(getVariables());
  }

  @Override
  public int getValueSetCount() {
    return getVariableEntityCount();
  }

  @Override
  public int getVariableEntityCount() {
    return Iterables.size(getVariableEntities());
  }

  @Override
  @SuppressWarnings("ChainOfInstanceofChecks")
  public boolean hasValueSet(@Nullable VariableEntity entity) {
    if(entity == null) return false;
    return getVariableEntities().contains(entity);
  }

  @Override
  public Iterable<ValueSet> getValueSets(Iterable<VariableEntity> entities) {
    List<VariableEntity> unmappedEntities = Collections.synchronizedList(Lists.newArrayList());
    StreamSupport.stream(entities.spliterator(), false) //
    .forEach(entity -> unmappedEntities.add(getVariableEntityMappingFunction().unapply(entity)));
    // do not use Guava functional stuff to avoid multiple iterations over valueSets
    List<ValueSet> valueSets = Collections.synchronizedList(Lists.newArrayList());
    StreamSupport.stream(super.getValueSets(unmappedEntities).spliterator(), false) //
    .forEach(valueSet -> {
        // replacing each ValueSet with one that points at the current View
        valueSet = getValueSetMappingFunction().apply(valueSet);
        // result of transformation might have returned a non-mappable entity
        if(valueSet != null && valueSet.getVariableEntity() != null) {
          valueSets.add(valueSet);
        }
    });
    return valueSets;
  }

  @Override
  public ValueSet getValueSet(VariableEntity entity) throws NoSuchValueSetException {
    VariableEntity unmapped = getVariableEntityMappingFunction().unapply(entity);
    if(unmapped == null) throw new NoSuchValueSetException(this, entity);

    ValueSet valueSet = super.getValueSet(unmapped);
    if(!getWhereClause().where(valueSet, this)) throw new NoSuchValueSetException(this, entity);

    return getValueSetMappingFunction().apply(valueSet);
  }

  @Override
  public Timestamps getValueSetTimestamps(VariableEntity entity) throws NoSuchValueSetException {
    VariableEntity unmapped = getVariableEntityMappingFunction().unapply(entity);
    if(unmapped == null) throw new NoSuchValueSetException(this, entity);

    return super.getValueSetTimestamps(unmapped);
  }

  @Override
  public Iterable<Timestamps> getValueSetTimestamps(SortedSet<VariableEntity> entities) {
    List<Timestamps> timestamps = Lists.newArrayList();
    for (VariableEntity entity : entities) {
      timestamps.add(getValueSetTimestamps(entity));
    }
    return timestamps;
  }

  @Override
  public Iterable<Variable> getVariables() {
    if(from instanceof JoinTable) {
      ((JoinTable) from).analyseVariables();
    }
    return isViewOfDerivedVariables() ? getListVariables() : getSelectVariables();
  }

  private Iterable<Variable> getSelectVariables() {
    return Iterables.filter(super.getVariables(), input -> getSelectClause().select(input));
  }

  private Iterable<Variable> getListVariables() {
    Collection<Variable> listVariables = new LinkedHashSet<>();
    for(VariableValueSource variableValueSource : getListClause().getVariableValueSources()) {
      listVariables.add(variableValueSource.getVariable());
    }
    return listVariables;
  }

  @Override
  public boolean hasVariable(@SuppressWarnings("ParameterHidesMemberVariable") String name) {
    try {
      getVariable(name);
      return true;
    } catch(NoSuchVariableException e) {
      return false;
    }
  }

  @Override
  public Variable getVariable(String variableName) throws NoSuchVariableException {
    return isViewOfDerivedVariables() //
        ? getListVariable(variableName) //
        : getSelectVariable(variableName);
  }

  private Variable getSelectVariable(String variableName) throws NoSuchVariableException {
    Variable variable = super.getVariable(variableName);
    if(getSelectClause().select(variable)) {
      return variable;
    }
    throw new NoSuchVariableException(variableName);
  }

  private Variable getListVariable(String variableName) throws NoSuchVariableException {
    return getListClause().getVariableValueSource(variableName).getVariable();
  }

  @Override
  public Value getValue(Variable variable, ValueSet valueSet) {
    if(isViewOfDerivedVariables()) {
      return getListClauseValue(variable, valueSet);
    }
    if(!getWhereClause().where(valueSet, this)) {
      throw new NoSuchValueSetException(this, valueSet.getVariableEntity());
    }
    return super.getValue(variable, getValueSetMappingFunction().unapply(valueSet));
  }

  private Value getListClauseValue(Variable variable, ValueSet valueSet) {
    return getListClauseVariableValueSource(variable.getName()).getValue(valueSet);
  }

  private VariableValueSource getListClauseVariableValueSource(String variableName) {
    VariableValueSource variableValueSource = getListClause().getVariableValueSource(variableName);
    return getVariableValueSourceMappingFunction().apply(variableValueSource);
  }

  @Override
  public VariableValueSource getVariableValueSource(String variableName) throws NoSuchVariableException {
    if(isViewOfDerivedVariables()) {
      return getListClauseVariableValueSource(variableName);
    }

    // Call getVariable(variableName) to check the SelectClause (if there is one). If the specified variable
    // is not selected by the SelectClause, this will result in a NoSuchVariableException.
    getVariable(variableName);

    // Variable "survived" the SelectClause. Go ahead and call the base class method.
    return getVariableValueSourceMappingFunction().apply(super.getVariableValueSource(variableName));
  }

  @Override
  public synchronized Set<VariableEntity> getVariableEntities() {
    Value tableWrapperLastUpdate = getTimestamps().getLastUpdate();
    VariableEntitiesCache eCache = getVariableEntitiesCache();
    if(eCache == null || !eCache.isUpToDate(tableWrapperLastUpdate)) {
      eCache = new VariableEntitiesCache(loadVariableEntities(), tableWrapperLastUpdate);
      if(MagmaEngine.get().hasExtension(MagmaCacheExtension.class)) {
        MagmaCacheExtension cacheExtension = MagmaEngine.get().getExtension(MagmaCacheExtension.class);
        if(cacheExtension.hasVariableEntitiesCache()) {
          cacheExtension.getVariableEntitiesCache().put(getTableCacheKey(), eCache);
        } else {
          variableEntitiesCache = eCache;
        }
      } else {
        variableEntitiesCache = eCache;
      }
    }
    return eCache.getEntities();
  }

  protected String getTableCacheKey() {
    String key = getTableReference() + ";class=" + getClass().getName();
    log.debug("tableCacheKey={}", key);
    return key;
  }

  private VariableEntitiesCache getVariableEntitiesCache() {
    if(MagmaEngine.get().hasExtension(MagmaCacheExtension.class)) {
      MagmaCacheExtension cacheExtension = MagmaEngine.get().getExtension(MagmaCacheExtension.class);
      if(!cacheExtension.hasVariableEntitiesCache()) return variableEntitiesCache;
      Cache.ValueWrapper wrapper = cacheExtension.getVariableEntitiesCache().get(getTableCacheKey());
      return wrapper == null ? variableEntitiesCache : (VariableEntitiesCache) wrapper.get();
    } else {
      return variableEntitiesCache;
    }
  }

  protected Set<VariableEntity> loadVariableEntities() {
    // do not use Guava functional stuff to avoid multiple iterations over entities
    Set<VariableEntity> entities = Sets.newConcurrentHashSet();
    if(hasVariables()) {
      StreamSupport.stream(super.getVariableEntities().spliterator(), false) //
          .forEach(entity -> {
            // filter the resulting entities to remove the ones for which hasValueSet() is false
            // (usually due to a where clause)
            boolean hasValueSet = true;
            if(getWhereClause() instanceof AllClause) {
              hasValueSet = true;
            } else if(getWhereClause() instanceof NoneClause) {
              hasValueSet = false;
            } else {
              ValueSet valueSet = super.getValueSet(entity);
              hasValueSet = getWhereClause().where(valueSet, this);
            }

            if(hasValueSet) {
              VariableEntity mappedEntity = getVariableEntityMappingFunction().apply(entity);
              entities.add(mappedEntity);
            }
          });
    }
    return entities;
  }

  public void setDatasource(ViewAwareDatasource datasource) {
    viewDatasource = datasource;
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  @SuppressWarnings("ConstantConditions")
  public void setSelectClause(@NotNull SelectClause selectClause) {
    Preconditions.checkArgument(selectClause != null, "null selectClause");
    select = selectClause;
  }

  @SuppressWarnings("ConstantConditions")
  public void setWhereClause(@NotNull WhereClause whereClause) {
    Preconditions.checkArgument(whereClause != null, "null whereClause");
    where = whereClause;
  }

  @SuppressWarnings("ConstantConditions")
  public void setListClause(@NotNull ListClause listClause) {
    Preconditions.checkArgument(listClause != null, "null listClause");
    variables = listClause;
  }

  @NotNull
  @Override
  public BijectiveFunction<VariableEntity, VariableEntity> getVariableEntityMappingFunction() {
    return BijectiveFunctions.identity();
  }

  @NotNull
  @Override
  public BijectiveFunction<ValueSet, ValueSet> getValueSetMappingFunction() {
    return new BijectiveFunction<ValueSet, ValueSet>() {

      @Override
      public ValueSet unapply(@SuppressWarnings("ParameterHidesMemberVariable") ValueSet from) {
        return ((ValueSetWrapper) from).getWrappedValueSet();
      }

      @Override
      public ValueSet apply(@SuppressWarnings("ParameterHidesMemberVariable") ValueSet from) {
        return new ValueSetWrapper(View.this, from);
      }
    };
  }

  @NotNull
  @Override
  public BijectiveFunction<VariableValueSource, VariableValueSource> getVariableValueSourceMappingFunction() {
    return new BijectiveFunction<VariableValueSource, VariableValueSource>() {
      @Override
      public VariableValueSource apply(@SuppressWarnings("ParameterHidesMemberVariable") VariableValueSource from) {
        return new ViewVariableValueSource(from);
      }

      @Override
      public VariableValueSource unapply(@SuppressWarnings("ParameterHidesMemberVariable") VariableValueSource from) {
        return ((VariableValueSourceWrapper) from).getWrapped();
      }
    };
  }

  private boolean hasVariables() {
    return !(select instanceof NoneClause) || variables.getVariableValueSources().iterator().hasNext();
  }

  protected class ViewVariableValueSource extends AbstractVariableValueSourceWrapper {

    public ViewVariableValueSource(VariableValueSource wrapped) {
      super(wrapped);
    }

    @NotNull
    @Override
    public Value getValue(ValueSet valueSet) {
      return getWrapped().getValue(getValueSetMappingFunction().unapply(valueSet));
    }

    @NotNull
    @Override
    public VectorSource asVectorSource() {
      return new ViewVectorSource(super.asVectorSource());
    }

    private class ViewVectorSource implements VectorSource {

      private final VectorSource wrapped;

      private SortedSet<VariableEntity> mappedEntities;

      private ViewVectorSource(VectorSource wrapped) {
        this.wrapped = wrapped;
      }

      @Override
      public ValueType getValueType() {
        return wrapped.getValueType();
      }

      @Override
      public Iterable<Value> getValues(SortedSet<VariableEntity> entities) {
        return wrapped.getValues(getMappedEntities(entities));
      }

      private SortedSet<VariableEntity> getMappedEntities(Iterable<VariableEntity> entities) {
        if(mappedEntities == null) {
          mappedEntities = Collections.synchronizedSortedSet(Sets.newTreeSet());
          StreamSupport.stream(entities.spliterator(), false) // not parallel to preserve entities order
           .forEach(entity -> mappedEntities.add(getVariableEntityMappingFunction().unapply(entity)));
        }
        return mappedEntities;
      }

      @Override
      @SuppressWarnings("SimplifiableIfStatement")
      public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj == null || getClass() != obj.getClass()) return false;
        return wrapped.equals(((ViewVectorSource) obj).wrapped);
      }

      @Override
      public int hashCode() {
        return wrapped.hashCode();
      }
    }

  }

  //
  // Builder
  //

  @SuppressWarnings({ "UnusedDeclaration", "StaticMethodOnlyUsedInOneClass" })
  public static class Builder {

    private final String name;

    private final ValueTable[] from;

    private String[] innerFrom;

    private SelectClause selectClause;

    private WhereClause whereClause;

    private ListClause listClause;

    public Builder(String name, @NotNull ValueTable... from) {
      this.name = name;
      this.from = from;
    }

    public static Builder newView(String name, @NotNull ValueTable... from) {
      return new Builder(name, from);
    }

    public static Builder newView(String name, @NotNull List<ValueTable> from) {
      return new Builder(name, from.toArray(new ValueTable[from.size()]));
    }

    public Builder select(@NotNull SelectClause selectClause) {
      this.selectClause = selectClause;
      return this;
    }

    public Builder innerFrom(String... tableReferences) {
      this.innerFrom = tableReferences;
      return this;
    }

    public Builder innerFrom(List<String> tableReferences) {
      this.innerFrom = tableReferences.toArray(new String[tableReferences.size()]);
      return this;
    }

    public Builder where(@NotNull WhereClause whereClause) {
      this.whereClause = whereClause;
      return this;
    }

    public Builder cacheWhere() {
      whereClause = new CachingWhereClause(whereClause);
      return this;
    }

    public View build() {
      View view = new View(name, innerFrom,from);
      if (selectClause != null) view.setSelectClause(selectClause);
      if (listClause != null) view.setListClause(listClause);
      if (whereClause != null) view.setWhereClause(whereClause);
      return view;
    }

    public Builder list(ListClause listClause) {
      this.listClause = listClause;
      return this;
    }
  }

}
