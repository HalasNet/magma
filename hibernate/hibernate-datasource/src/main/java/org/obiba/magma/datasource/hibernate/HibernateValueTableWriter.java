/**
 *
 */
package org.obiba.magma.datasource.hibernate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.json.JSONException;
import org.json.JSONObject;
import org.obiba.core.service.impl.hibernate.AssociationCriteria;
import org.obiba.core.service.impl.hibernate.AssociationCriteria.Operation;
import org.obiba.magma.MagmaRuntimeException;
import org.obiba.magma.NoSuchVariableException;
import org.obiba.magma.Value;
import org.obiba.magma.ValueTableWriter;
import org.obiba.magma.Variable;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.datasource.hibernate.converter.HibernateMarshallingContext;
import org.obiba.magma.datasource.hibernate.converter.VariableConverter;
import org.obiba.magma.datasource.hibernate.converter.VariableEntityConverter;
import org.obiba.magma.datasource.hibernate.domain.ValueSetBinaryValue;
import org.obiba.magma.datasource.hibernate.domain.ValueSetState;
import org.obiba.magma.datasource.hibernate.domain.ValueSetValue;
import org.obiba.magma.datasource.hibernate.domain.VariableEntityState;
import org.obiba.magma.datasource.hibernate.domain.VariableState;
import org.obiba.magma.type.BinaryType;
import org.obiba.magma.type.TextType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

class HibernateValueTableWriter implements ValueTableWriter {

  private final HibernateValueTable valueTable;

  private final HibernateValueTableTransaction transaction;

  private final VariableConverter variableConverter = VariableConverter.getInstance();

  private final Session session;

  private final HibernateVariableValueSourceFactory valueSourceFactory;

  private boolean errorOccurred = false;

  private final HibernateMarshallingContext context;

  HibernateValueTableWriter(HibernateValueTableTransaction transaction) {
    if(transaction == null) throw new IllegalArgumentException("transaction cannot be null");
    this.transaction = transaction;
    valueTable = transaction.getValueTable();

    session = valueTable.getDatasource().getSessionFactory().getCurrentSession();
    valueSourceFactory = new HibernateVariableValueSourceFactory(valueTable);
    if(session.getFlushMode() != FlushMode.MANUAL) {
      session.setFlushMode(FlushMode.MANUAL);
    }

    context = valueTable.createContext();
  }

  @Override
  public ValueSetWriter writeValueSet(VariableEntity entity) {
    return new HibernateValueSetWriter(entity);
  }

  @Override
  public VariableWriter writeVariables() {
    return new HibernateVariableWriter();
  }

  @Override
  public void close() throws IOException {
  }

  private class HibernateVariableWriter implements VariableWriter {

    private HibernateVariableWriter() {
    }

    @Override
    public void writeVariable(Variable variable) {
      if(variable == null) throw new IllegalArgumentException("variable cannot be null");
      if(!valueTable.isForEntityType(variable.getEntityType())) {
        throw new IllegalArgumentException(
            "Wrong entity type for variable '" + variable.getName() + "': " + valueTable.getEntityType() +
                " expected, " + variable.getEntityType() + " received.");
      }

      // add or update variable
      errorOccurred = true;
      VariableState state = variableConverter.marshal(variable, context);
      transaction.addSource(valueSourceFactory.createSource(state));
      errorOccurred = false;
    }

    @Override
    public void close() throws IOException {
      if(!errorOccurred) {
        session.flush();
        session.clear();
      }
    }
  }

  private class HibernateValueSetWriter implements ValueSetWriter {

    private final VariableEntityConverter entityConverter = new VariableEntityConverter();

    private final ValueSetState valueSetState;

    @Nonnull
    private final VariableEntity entity;

    private final boolean isNewValueSet;

    private final Map<String, ValueSetValue> values;

    private HibernateValueSetWriter(@Nonnull VariableEntity entity) {
      if(entity == null) throw new IllegalArgumentException("entity cannot be null");
      this.entity = entity;
      // find entity or create it
      VariableEntityState variableEntityState = entityConverter.marshal(entity, context);

      AssociationCriteria criteria = AssociationCriteria.create(ValueSetState.class, session)
          .add("valueTable", Operation.eq, valueTable.getValueTableState())
          .add("variableEntity", Operation.eq, variableEntityState);

      // Will update version timestamp if it exists
      ValueSetState state = (ValueSetState) criteria.getCriteria().setLockMode(LockMode.PESSIMISTIC_FORCE_INCREMENT)
          .uniqueResult();
      if(state == null) {
        state = new ValueSetState(valueTable.getValueTableState(), variableEntityState);
        // Persists the ValueSet
        session.save(state);
        values = Maps.newHashMap();
        isNewValueSet = true;
      } else {
        values = Maps.newHashMap(state.getValueMap());
        isNewValueSet = false;
      }
      valueSetState = state;
    }

    @Override
    public void writeValue(@Nonnull Variable variable, @Nonnull Value value) {
      if(variable == null) throw new IllegalArgumentException("variable cannot be null");
      if(value == null) throw new IllegalArgumentException("value cannot be null");

      try {
        VariableState variableState = variableConverter.getStateForVariable(variable, valueTable.createContext());
        if(variableState == null) {
          throw new NoSuchVariableException(valueTable.getName(), variable.getName());
        }
        ValueSetValue valueSetValue = values.get(variable.getName());
        if(valueSetValue == null) {
          if(!value.isNull()) {
            createValue(variable, value, variableState);
          }
        } else {
          updateValue(variable, value, valueSetValue);
        }
      } catch(RuntimeException e) {
        errorOccurred = true;
        throw e;
      } catch(Error e) {
        errorOccurred = true;
        throw e;
      }
    }

    private void createValue(Variable variable, Value value, VariableState variableState) {
      ValueSetValue valueSetValue;
      valueSetValue = new ValueSetValue(variableState, valueSetState);

      if(BinaryType.get().equals(value.getValueType())) {
        writeBinaryValue(valueSetValue, value, false);
      } else {
        valueSetValue.setValue(value);
      }

      valueSetState.getValues().add(valueSetValue);
      values.put(variable.getName(), valueSetValue);
    }

    private void updateValue(Variable variable, Value value, ValueSetValue valueSetValue) {
      if(value.isNull()) {
        removeValue(variable, valueSetValue);
      } else {
        if(BinaryType.get().equals(value.getValueType())) {
          writeBinaryValue(valueSetValue, value, true);
        } else {
          valueSetValue.setValue(value);
        }
      }
    }

    private void removeValue(Variable variable, ValueSetValue valueSetValue) {
      valueSetState.getValues().remove(valueSetValue);
      values.remove(variable.getName());
    }

    @SuppressWarnings("ConstantConditions")
    private void writeBinaryValue(ValueSetValue valueSetValue, Value value, boolean isUpdate) {
      if(value.isSequence()) {
        List<Value> sequenceValues = Lists.newArrayList();
        int occurrence = 0;
        for(Value valueOccurrence : value.asSequence().getValue()) {
          sequenceValues.add(createBinaryValue(valueSetValue, valueOccurrence, occurrence++, isUpdate));
        }
        valueSetValue.setValue(TextType.get().sequenceOf(sequenceValues));
      } else {
        valueSetValue.setValue(createBinaryValue(valueSetValue, value, 0, isUpdate));
      }
    }

    private Value createBinaryValue(ValueSetValue valueSetValue, Value inputValue, int occurrence, boolean isUpdate) {
      Value value = null;
      ValueSetBinaryValue binaryValue = isUpdate ? findBinaryValue(valueSetValue, occurrence) : null;
      if(binaryValue == null) {
        binaryValue = createBinaryValue(valueSetValue, inputValue, occurrence);
      } else if(inputValue.getValue() == null) {
        session.delete(binaryValue);
      }
      if(binaryValue == null) {
        // can be null if empty byte[]
        value = TextType.get().nullValue();
      } else {
        session.save(binaryValue);
        value = getBinaryMetadata(binaryValue);
      }
      return value;
    }

    private ValueSetBinaryValue findBinaryValue(ValueSetValue valueSetValue, int occurrence) {
      return (ValueSetBinaryValue) session.getNamedQuery("findBinaryByValueSetValueAndOccurrence") //
          .setParameter("variableId", valueSetValue.getVariable().getId()) //
          .setParameter("valueSetId", valueSetValue.getValueSet().getId()) //
          .setParameter("occurrence", occurrence) //
          .uniqueResult();
    }

    @Nullable
    private ValueSetBinaryValue createBinaryValue(ValueSetValue valueSetValue, Value value, int occurrence) {
      Object bytes = value.getValue();
      if(bytes == null) return null;
      ValueSetBinaryValue binaryValue = new ValueSetBinaryValue(valueSetValue, occurrence);
      binaryValue.setValue((byte[]) bytes);
      return binaryValue;
    }

    private Value getBinaryMetadata(ValueSetBinaryValue binaryValue) {
      try {
        JSONObject properties = new JSONObject();
        properties.put("size", binaryValue == null ? 0 : binaryValue.getSize());
        return TextType.get().valueOf(properties.toString());
      } catch(JSONException e) {
        throw new MagmaRuntimeException(e);
      }
    }

    @Override
    public void close() throws IOException {
      if(!errorOccurred) {
        if(isNewValueSet) {
          // Make the entity visible within this transaction
          transaction.addEntity(entity);
        }
        // Persists valueSetState
        session.flush();
        // Empty the Session so we don't fill it up
        session.evict(valueSetState);
      }
    }

  }

}