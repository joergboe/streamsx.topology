/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2015  
 */
package com.ibm.streamsx.topology.generator.spl;

import static com.ibm.streamsx.topology.internal.functional.ops.FunctionFunctor.FUNCTIONAL_LOGIC_PARAM;
import static com.ibm.streamsx.topology.internal.core.SubmissionParameter.TYPE_SUBMISSION_PARAMETER;

import java.util.HashMap;
import java.util.Map;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.json.java.OrderedJSONObject;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streamsx.topology.internal.functional.ops.SubmissionParameterManager;

/**
 * A Submission Time Value is the SPL realization of a "Submission Parameter".
 */
public class SubmissionTimeValue {
    /** Special operator parameter type for conveying
     * submission parameter values in an SPL functional operator
     * parameter, ultimately for access by functional logic.
     * <p>
     * The operator parameter's value is a 
     * JSONArray of {@code spOpParamName, (rstring) $+spOpParamName} pairs
     * for each submission parameter in the topology.
     * <p>
     * See {@link SubmissionParameterManager}
     */
    public static final String TYPE_SPL_SUBMISSION_PARAMS = "__spl_submissionParams";
    /** map<spOpParamName,opParam> opParam has type TYPE_SUBMISSION_PARAMETER */
    private final Map<String,JSONObject> allSubmissionParams;
    /** An opParam of type TYPE_SPL_SUBMISSION_PARAMS */
    private final JSONObject submissionParamsParam;
    /** map<opName,opJsonObject> */
    private Map<String,JSONObject> functionalOps = new HashMap<>();
    
    /**
     * Create a json operator parameter name for the submission parameter name. 
     * @param spName the submission parameter name
     * @return the operator parameter name
     */
    public static String mkOpParamName(String spName) {
        spName = spName.replace('.', '_');
        return "__jaa_stv_" + SPLGenerator.getSPLCompatibleName(spName);
    }

    SubmissionTimeValue(JSONObject graph) {
        allSubmissionParams = getAllSubmissionParams(graph);
        submissionParamsParam = mkSubmissionParamsParam();
    }
    
    /**
     * Get a collection of all of the submission parameters used in the graph.
     * @param graph
     * @return {@code map<spOpParamName,spParam>}
     */
    private Map<String,JSONObject> getAllSubmissionParams(JSONObject graph) {
        Map<String,JSONObject> all = new HashMap<>();
        JSONObject params = (JSONObject) graph.get("parameters");
        if (params != null) {
            for (Object o : params.keySet()) {
                String key = (String) o;
                JSONObject param = (JSONObject) params.get(key);
                if (TYPE_SUBMISSION_PARAMETER.equals(param.get("type"))) {
                    JSONObject sp = (JSONObject) param.get("value");
                    all.put(mkOpParamName((String)sp.get("name")), param);
                }
            }
        }
        return all;
    }
    
    /**
     * Create a {@link TYPE_SPL_SUBMISSION_PARAMS} operator parameter
     * @return the parameter. null if no submission parameters.
     */
    private JSONObject mkSubmissionParamsParam() {
        if (allSubmissionParams.isEmpty())
            return null;
        JSONArray ja = new JSONArray(allSubmissionParams.size() * 2);
        for (Object key : allSubmissionParams.keySet()) {
            String opParamName = (String) key;
            JSONObject spParam = (JSONObject) allSubmissionParams.get(opParamName);
            JSONObject spval = (JSONObject) spParam.get("value");
            ja.add(opParamName);
            ja.add("(rstring) "+generateCompParamName(spval));
        }
        JSONObject param = new JSONObject();
        param.put("type", TYPE_SPL_SUBMISSION_PARAMS);
        param.put("value", ja);
        return param;
    }
    
    /**
     * Enrich the json composite operator definition's parameters
     * to include parameters for submission parameters.
     * <p>
     * The composite is augmented with a TYPE_SUBMISSION_PARAMETER parameter
     * for each submission parameter used within the composite - e.g, as
     * a parallel width value or SPL operator parameter value.
     * <p>
     * If the composite has any functional operator children, enrich
     * the composite to have declarations for all submission parameters.
     * Also accumulate such children and make them available via
     * {@link #getFunctionalOps()}.
     * 
     * @param composite the composite definition
     */
    @SuppressWarnings("unchecked")
    void addJsonParamDefs(JSONObject composite) {
        // scan immediate children ops for submission param use
        // and add corresponding param definitions to the composite.
        // Also, if the op has functional logic, enrich the op too...
        // and further enrich the composite.
        
        if (allSubmissionParams.isEmpty())
            return;
        
        // scan for spParams
        JSONObject spParams = new JSONObject();
        boolean addedAll = false;
        JSONArray operators = (JSONArray) composite.get("operators");
        for (Object op : operators) {
            JSONObject jop = (JSONObject)op;
            JSONObject params = (JSONObject) jop.get("parameters");
            if (params != null) {
                boolean addAll = false;
                for (Object pname : params.keySet()) {
                    // if functional logic add "submissionParameters" param
                    if (params.get(FUNCTIONAL_LOGIC_PARAM) != null) {
                        functionalOps.put((String)jop.get("name"), jop);
                        addAll = true;
                    }
                    else {
                        JSONObject param = (JSONObject) params.get(pname);
                        String type = (String) param.get("type");
                        if (TYPE_SUBMISSION_PARAMETER.equals(type)) {
                            JSONObject spval = (JSONObject) param.get("value");
                            pname = mkOpParamName((String)spval.get("name"));
                            spParams.put(pname, param);
                        }
                    }
                }
                if (addAll && !addedAll) {
                    spParams.putAll(allSubmissionParams);
                    addedAll = true;
                }
            }
            Boolean isParallel = (Boolean) jop.get("parallelOperator"); 
            if (isParallel != null && isParallel) {
                Object width = jop.get("width");
                if (width instanceof JSONObject) {
                    JSONObject jwidth = (JSONObject)width; 
                    Object type = jwidth.get("type");
                    if (TYPE_SUBMISSION_PARAMETER.equals(type)) {
                        JSONObject spval = (JSONObject) jwidth.get("value");
                        String pname = mkOpParamName((String)spval.get("name")); 
                        spParams.put(pname, jwidth);
                    }
                }
            }
        }
        
        // augment the composite's parameters
        JSONObject params = (JSONObject) composite.get("parameters");
        if (params == null && spParams.size() > 0) {
            params = new OrderedJSONObject();
            composite.put("parameters", params);
        }
        for (Object pname : spParams.keySet()) {
            if (!params.keySet().contains(pname))
                params.put(pname, spParams.get(pname));
        }
        
        // make the results of our efforts available to addJsonInstanceParams
        composite.put(TYPE_SPL_SUBMISSION_PARAMS, spParams);
    }

    /**
     * Akin to addJsonParamDefs(), enrich the json composite operator instance's
     * parameters with submission parameter references.
     * @param compInstance the composite instance
     * @param composite the composite definition
     */
    void addJsonInstanceParams(JSONObject compInstance, JSONObject composite) {
        JSONObject spParams = (JSONObject) composite.get(TYPE_SPL_SUBMISSION_PARAMS);
        if (spParams != null) {
            JSONObject opParams = (JSONObject) compInstance.get("parameters");
            if (opParams == null) {
                opParams = new JSONObject();
                compInstance.put("parameters", opParams);
            }
            for (Object pname : spParams.keySet()) {
                JSONObject spParam = (JSONObject) spParams.get(pname);
                // need to end up generating: __jaa_stv_foo : $__jaa_stv_foo;
                JSONObject spval = (JSONObject) spParam.get("value");
                pname = mkOpParamName((String)spval.get("name")); 
                opParams.put(pname, spParam);
            }
        }
    }
    
    /** Get the graph's submission parameters in the form of a
     * operator parameter of TYPE_SPL_SUBMISSION_PARAMS
     * @return the op parameter. null if no submission params in the topology.
     */
    JSONObject getSubmissionParamsParam() {
        return submissionParamsParam;
    }
    
    /** Get the list of functional ops learned by {@link #addJsonParamDefs(JSONObject)}.
     * @return the collection of functional ops map<opName, opJsonObject>
     */
    Map<String,JSONObject> getFunctionalOps() {
        return functionalOps;
    }
    
    /**
     * Generate a submission time value SPL param value definition
     * in a main composite.
     * <p>
     * e.g.,
     * <pre>{@code
     *  param
     *      expression<uint32> $__jaa_stv_foo : (uint32) getSubmissionTimeValue("foo", 3)
     * }</pre>
     * @param spval JSONObject for the submission parameter's value
     * @param sb
     */
    void generateMainDef(JSONObject spval, StringBuilder sb) {
        String paramName = generateCompParamName(spval);
        String spName = SPLGenerator.stringLiteral((String) spval.get("name"));
        String metaType = (String) spval.get("metaType");
        String splType = MetaType.valueOf(metaType).getLanguageType();
        Object defaultValue = spval.get("defaultValue");
        sb.append(String.format("expression<%s> %s : ", splType, paramName));
        if (defaultValue == null)
            sb.append(String.format("(%s) getSubmissionTimeValue(%s)", splType, spName));
        else {
            if (metaType.startsWith("UINT"))
                defaultValue = SPLGenerator.unsignedString(defaultValue);
            defaultValue = SPLGenerator.stringLiteral(defaultValue.toString());
            sb.append(String.format("(%s) getSubmissionTimeValue(%s, %s)", splType, spName, defaultValue));
        }
    }
    
    /**
     * Generate a submission time value SPL param value definition
     * in an inner (non-main) composite definition.
     * <p>
     * e.g.,
     * <pre>{@code
     *  param
     *      expression<uint32> $__jaa_stv_foo
     * }</pre>
     * @param spval JSONObject for the submission parameter's value
     * @param sb
     */
    void generateInnerDef(JSONObject spval, StringBuilder sb) {
        String paramName = generateCompParamName(spval);
        String metaType = (String) spval.get("metaType");
        String splType = MetaType.valueOf(metaType).getLanguageType();
        sb.append(String.format("expression<%s> %s", splType, paramName));
    }
    
    /**
     * Generate a $__jaa_stv_... composite parameter name
     * for the submission time value parameter. 
     * <p>
     * <pre><code>
     *  composite {
     *  param expression<int32> $__jaa_stv_foo = (int32) getSubmissionTimeValue(...)
     *  graph      
     *     {@literal @}parallel(width=$__jaa_stv_foo)
     * </code></pre>
     * @param spval JSONObject for the submission parameter's value
     * @return the name
     */
    String generateCompParamName(JSONObject spval) {
        return "$" + mkOpParamName((String)spval.get("name"));
    }

}
