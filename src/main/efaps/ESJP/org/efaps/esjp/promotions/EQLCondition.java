/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.efaps.esjp.promotions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.IUIValue;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.common.uiform.Field_Base.DropDownPosition;
import org.efaps.esjp.promotions.utils.Promotions;
import org.efaps.util.EFapsException;
import org.efaps.util.UUIDUtil;

@EFapsUUID("f3eb249e-5c4f-45fc-bfbd-46e72656e9ac")
@EFapsApplication("eFapsApp-Promotions")
public class EQLCondition
{

    public Return getAttributeDefinitionTypeOptionListFieldValue(final Parameter parameter)
        throws EFapsException
    {
        final var ret = new Return();
        final List<DropDownPosition> values = new ArrayList<>();
        final var properties = Promotions.EQL_ATTRDEF.get();
        final var types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        final var labels = PropertiesUtil.analyseProperty(properties, "Label", 0);
        for (final var entry : types.entrySet()) {
            Long typeId;
            if (UUIDUtil.isUUID(entry.getValue())) {
                typeId = Type.get(UUID.fromString(entry.getValue())).getId();
            } else {
                typeId = Type.get(entry.getValue()).getId();
            }
            values.add(new DropDownPosition(typeId, labels.get(entry.getKey())));
        }
        ret.put(ReturnValues.VALUES, values);
        return ret;
    }

    public Return getAttributeDefinitionTypeFieldFormat(final Parameter parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final IUIValue value = (IUIValue) parameter.get(ParameterValues.UIOBJECT);
        if (value.getObject() instanceof Long) {
            final var type = Type.get((Long) value.getObject());
            if (type != null) {
                final var properties = Promotions.EQL_ATTRDEF.get();
                final var types = PropertiesUtil.analyseProperty(properties, "Type", 0);
                final var labels = PropertiesUtil.analyseProperty(properties, "Label", 0);
                final var keyOpt = types
                                .entrySet()
                                .stream()
                                .filter(entry -> entry.getValue().equals(type.getName())
                                                || entry.getValue().equals(type.getUUID().toString()))
                                .map(Map.Entry::getKey)
                                .findFirst();
                if (keyOpt.isPresent()) {
                    ret.put(ReturnValues.VALUES, labels.get(keyOpt.get()));
                } else {
                    ret.put(ReturnValues.VALUES, type.getLabel());
                }
            } else {
                ret.put(ReturnValues.VALUES, "");
            }
        }
        return ret;
    }

    public Return updateDropDown4AttributeDefinitionType(final Parameter parameter)
        throws EFapsException
    {
        final var ret = new Return();
        final var values = new ArrayList<Map<String, Object>>();
        final var map = new HashMap<String, Object>();

        final var attrDefType = parameter.getParameterValue("attributeDefinitionType");
        final var type = Type.get(Long.valueOf(attrDefType));
        final StringBuilder js = new StringBuilder()
                        .append("new Array('").append(0).append("'");

        final var eval = EQL.builder().print().query(type.getName())
                        .select()
                        .attribute(CIERP.AttributeDefinitionAbstract.Value)
                        .evaluate();
        while (eval.next()) {
            js.append(",'").append(eval.inst().getId()).append("','")
                            .append(eval.<String>get(CIERP.AttributeDefinitionAbstract.Value))
                            .append("'");
        }
        js.append(")");
        values.add(map);
        map.put("attributeDefinitionValue", js.toString());

        ret.put(ReturnValues.VALUES, values);
        return ret;
    }

    public Return getAttributeDefinitionValueFieldFormat(final Parameter parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final IUIValue value = (IUIValue) parameter.get(ParameterValues.UIOBJECT);
        if (value.getObject() instanceof Long) {
            final var valueId = (Long) value.getObject();
            final var eval = EQL.builder()
                            .print()
                            .query(CIERP.AttributeDefinitionAbstract)
                            .where()
                            .attribute(CIERP.AttributeDefinitionAbstract.ID).eq(valueId)
                            .select()
                            .attribute(CIERP.AttributeDefinitionAbstract.Value)
                            .evaluate();
            if (eval.next()) {
                ret.put(ReturnValues.VALUES, eval.get(CIERP.AttributeDefinitionAbstract.Value));
            }

        }
        return ret;
    }

    public Return create(final Parameter parameter)
        throws EFapsException
    {
        final var ret = new Create().execute(parameter);
        final Instance eqlAttributeDefinitionInst = (Instance) ret.get(ReturnValues.INSTANCE);

        final var eval = EQL.builder()
                        .print(eqlAttributeDefinitionInst)
                        .attribute(CIPromo.EQLAttributeDefinition.AttributeDefinitionType,
                                        CIPromo.EQLAttributeDefinition.AttributeDefinitionValue)
                        .evaluate();
        eval.next();
        final Long typeId = eval.get(CIPromo.EQLAttributeDefinition.AttributeDefinitionType);
        final Long valueId = eval.get(CIPromo.EQLAttributeDefinition.AttributeDefinitionValue);

        final var type = Type.get(typeId);
        final var properties = Promotions.EQL_ATTRDEF.get();
        final var types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        final var labels = PropertiesUtil.analyseProperty(properties, "Label", 0);
        final var keyOpt = types
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().equals(type.getName())
                                        || entry.getValue().equals(type.getUUID().toString()))
                        .map(Map.Entry::getKey)
                        .findFirst();
        String typeLabel;
        if (keyOpt.isPresent()) {
            typeLabel = labels.get(keyOpt.get());
        } else {
            typeLabel = type.getLabel();
        }

        final var eval2 = EQL.builder()
                        .print()
                        .query(CIERP.AttributeDefinitionAbstract)
                        .where()
                        .attribute(CIERP.AttributeDefinitionAbstract.ID).eq(valueId)
                        .select()
                        .attribute(CIERP.AttributeDefinitionAbstract.Value)
                        .evaluate();
        eval2.next();
        final var value = eval2.get(CIERP.AttributeDefinitionAbstract.Value);
        EQL.builder()
                        .update(eqlAttributeDefinitionInst)
                        .set(CIPromo.EQLAttributeDefinition.Description, String.format("%s == %s", typeLabel, value))
                        .execute();
        return ret;
    }

}
