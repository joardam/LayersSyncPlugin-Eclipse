package br.com.layersyncplugin.generator;

import java.util.Arrays;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

/**
 * Responsável por gerar e modificar o código-fonte das classes em diferentes camadas
 * usando a API JDT Core de forma segura.
 */
public class CodeGenerator {

    /**
     * Orquestra a criação de um método em todas as camadas fornecidas (Facade, Business, DataProvider).
     *
     * @param facadeFQN         Nome completo da classe Facade.
     * @param businessFQN       Nome completo da classe Business (pode ser nulo ou vazio).
     * @param dataProviderFQN    Nome completo da classe DataProvider (pode ser nulo ou vazio).
     * @param methodName         Nome do método a ser criado.
     * @param returnType         Tipo de retorno do método.
     * @param methodParameters   String formatada com os parâmetros do método.
     * @throws Exception se uma classe obrigatória não for encontrada ou ocorrer um erro na modificação.
     */
    public void generateMethodsForAllLayers(String facadeFQN, String businessFQN, String dataProviderFQN,
                                            String methodName, String returnType, String methodParameters) throws Exception {

        createMethodInLayer(facadeFQN, methodName, returnType, methodParameters, businessFQN);
        createMethodInLayer(businessFQN, methodName, returnType, methodParameters, dataProviderFQN);
        createMethodInLayer(dataProviderFQN, methodName, returnType, methodParameters, null);
    }

    /**
     * Cria um método em uma classe específica, sua interface e encadeia a chamada para a próxima camada.
     *
     * @param currentClassFQN  Nome completo da classe da camada atual.
     * @param methodName       Nome do método.
     * @param returnType       Tipo de retorno.
     * @param methodParameters Parâmetros do método.
     * @param nextLayerClassFQN Nome completo da classe da próxima camada (pode ser nulo).
     * @throws Exception se a classe ou interface não forem encontradas.
     */
    private void createMethodInLayer(String currentClassFQN, String methodName, String returnType, String methodParameters, String nextLayerClassFQN) throws Exception {
        if (currentClassFQN == null || currentClassFQN.trim().isEmpty()) {
            return;
        }

        IType classType = findType(currentClassFQN);
        if (classType == null) {
            throw new IllegalArgumentException("A classe '" + currentClassFQN + "' não foi encontrada no workspace.");
        }

        IType interfaceType = findImplementedInterface(classType);
        if (interfaceType == null) {
            throw new IllegalStateException("Nenhuma interface encontrada para a classe '" + currentClassFQN + "'. A geração foi abortada.");
        }

        addMethodToInterface(interfaceType, methodName, returnType, methodParameters);
        addMethodToClass(classType, methodName, returnType, methodParameters, nextLayerClassFQN);
    }

    /**
     * Adiciona a assinatura de um método a uma interface de forma segura.
     */
    private void addMethodToInterface(IType interfaceType, String methodName, String returnType, String methodParameters) throws JavaModelException {
         String methodSignature = String.format("public %s %s(%s);", returnType, methodName, methodParameters);
        interfaceType.createMethod(methodSignature, null, false, new NullProgressMonitor());
    }
    
    /**
     * Adiciona a implementação de um método a uma classe, criando a chamada para a próxima camada.
     */
    private void addMethodToClass(IType classType, String methodName, String returnType, String methodParameters, String nextLayerClassFQN) throws JavaModelException {
//      // Garante a importação da anotação @Override
//      classType.getCompilationUnit().createImport("java.lang.Override", null, new NullProgressMonitor());
    	String methodBody;

        if (nextLayerClassFQN != null && !nextLayerClassFQN.trim().isEmpty()) {
            IType nextLayerClass = findType(nextLayerClassFQN);
            if (nextLayerClass == null) {
                 throw new IllegalStateException("A classe da próxima camada '" + nextLayerClassFQN + "' não foi encontrada.");
            }
            
            IType nextLayerInterface = findImplementedInterface(nextLayerClass);
            if (nextLayerInterface == null) {
                throw new IllegalStateException("A interface para a classe da próxima camada '" + nextLayerClassFQN + "' não foi encontrada.");
            }

            classType.getCompilationUnit().createImport(nextLayerInterface.getFullyQualifiedName(), null, new NullProgressMonitor());

            String fieldName = findOrAddFieldFor(classType, nextLayerInterface);
            String parameterNames = extractParameterNames(methodParameters);
            
            String callStatement = String.format("return this.%s.%s(%s);", fieldName, methodName, parameterNames);
            if ("void".equalsIgnoreCase(returnType)) {
                 callStatement = String.format("this.%s.%s(%s);", fieldName, methodName, parameterNames);
            }

            methodBody = String.format("{\n\t%s\n}", callStatement);

        } else {
            methodBody = "{\n\t// TODO Auto-generated method stub\n\treturn null;\n}";
        }
        
        String methodImplementation = String.format(
            "@Override\npublic %s %s(%s) %s",
            returnType, methodName, methodParameters, methodBody
        );

        classType.createMethod(methodImplementation, null, false, new NullProgressMonitor());
    }
    
    /**
     * Procura por um campo do tipo da interface fornecida. Se não encontrar, cria um novo.
     * @return O nome do campo (existente ou novo).
     */
    private String findOrAddFieldFor(IType classType, IType interfaceType) throws JavaModelException {
        String interfaceName = interfaceType.getElementName();
        String expectedFieldName = Character.toLowerCase(interfaceName.charAt(1)) + interfaceName.substring(2);

        for (IField field : classType.getFields()) {
            if (field.getElementName().equals(expectedFieldName)) {
                return expectedFieldName;
            }
        }
        
        String fieldDeclaration = String.format("private %s %s;", interfaceName, expectedFieldName);
        classType.createField(fieldDeclaration, null, false, new NullProgressMonitor());
        
        return expectedFieldName;
    }

    /**
     * Extrai apenas os nomes dos parâmetros da string de declaração.
     * Ex: "String name, List<User> users" -> "name, users"
     */
    private String extractParameterNames(String methodParameters) {
        if (methodParameters == null || methodParameters.trim().isEmpty()) {
            return "";
        }
        
        String[] params = methodParameters.split(",");
        return Arrays.stream(params)
                     .map(String::trim)
                     .map(param -> param.split("\\s+")[1])
                     .reduce((p1, p2) -> p1 + ", " + p2)
                     .orElse("");
    }
    
    /**
     * Encontra a primeira interface implementada por uma classe.
     */
    private IType findImplementedInterface(IType classType) throws JavaModelException {
        String[] interfaceNames = classType.getSuperInterfaceNames();
        if (interfaceNames == null || interfaceNames.length == 0) {
            return null;
        }
        
        String[][] resolvedInterfaces = classType.resolveType(interfaceNames[0]);
        if (resolvedInterfaces == null || resolvedInterfaces.length == 0) {
            return null;
        }
        
        String qualifiedInterfaceName = resolvedInterfaces[0][0] + "." + resolvedInterfaces[0][1];
        return findType(qualifiedInterfaceName);
    }

    /**
     * Utiliza a SearchEngine do JDT para encontrar um IType pelo seu nome completo.
     */
    private IType findType(String fullyQualifiedName) throws JavaModelException {
        final IType[] result = new IType[1];
        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        SearchPattern pattern = SearchPattern.createPattern(fullyQualifiedName, IJavaSearchConstants.TYPE, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                if (match.getElement() instanceof IType) {
                    result[0] = (IType) match.getElement();
                }
            }
        };

        try {
            new SearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope, requestor, new NullProgressMonitor());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return result[0];
    }
}