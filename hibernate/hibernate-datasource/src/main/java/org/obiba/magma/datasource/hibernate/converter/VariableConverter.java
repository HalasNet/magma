package org.obiba.magma.datasource.hibernate.converter;

import org.obiba.core.service.impl.hibernate.AssociationCriteria;
import org.obiba.core.service.impl.hibernate.AssociationCriteria.Operation;
import org.obiba.magma.Category;
import org.obiba.magma.Variable;
import org.obiba.magma.Variable.Builder;
import org.obiba.magma.datasource.hibernate.domain.CategoryState;
import org.obiba.magma.datasource.hibernate.domain.VariableState;

public class VariableConverter extends AttributeAwareConverter implements HibernateConverter<VariableState, Variable> {

  public static VariableConverter getInstance() {
    return new VariableConverter();
  }

  public VariableState getStateForVariable(Variable variable, HibernateMarshallingContext context) {
    AssociationCriteria criteria = AssociationCriteria.create(VariableState.class, context.getSessionFactory().getCurrentSession()).add("valueTable", Operation.eq, context.getValueTable()).add("name", Operation.eq, variable.getName());
    return (VariableState) criteria.getCriteria().setCacheable(true).uniqueResult();
  }

  @Override
  public VariableState marshal(Variable variable, HibernateMarshallingContext context) {
    VariableState variableState = getStateForVariable(variable, context);
    if(variableState == null) {
      variableState = new VariableState(context.getValueTable(), variable);
    }

    addAttributes(variable, variableState);
    marshalCategories(variable, variableState);

    context.getSessionFactory().getCurrentSession().save(variableState);

    return variableState;
  }

  private void marshalCategories(Variable variable, VariableState variableState) {
    for(Category c : variable.getCategories()) {
      CategoryState categoryState = variableState.getCategory(c.getName());
      if(categoryState == null) {
        categoryState = new CategoryState(c.getName(), c.getCode(), c.isMissing());
        variableState.addCategory(categoryState);
      }
      addAttributes(c, categoryState);
    }
  }

  @Override
  public Variable unmarshal(VariableState variableState, HibernateMarshallingContext context) {
    Variable.Builder builder = Variable.Builder.newVariable(variableState.getName(), variableState.getValueType(), variableState.getEntityType());
    builder.mimeType(variableState.getMimeType()).occurrenceGroup(variableState.getOccurrenceGroup()).referencedEntityType(variableState.getReferencedEntityType()).unit(variableState.getUnit());
    if(variableState.isRepeatable()) {
      builder.repeatable();
    }

    buildAttributeAware(builder, variableState);
    unmarshalCategories(builder, variableState);
    return builder.build();
  }

  private void unmarshalCategories(Builder builder, VariableState variableState) {
    for(CategoryState categoryState : variableState.getCategories()) {
      Category.Builder categoryBuilder = Category.Builder.newCategory(categoryState.getName()).withCode(categoryState.getCode()).missing(categoryState.isMissing());
      buildAttributeAware(categoryBuilder, categoryState);
      builder.addCategory(categoryBuilder.build());
    }
  }

}
