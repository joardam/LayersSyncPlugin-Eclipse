package br.com.layersyncplugin.views;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import br.com.layersyncplugin.generator.CodeGenerator;

/**
 * View para sincronização de camadas e geração de métodos. Permite ao usuário
 * selecionar classes e definir uma assinatura de método para gerar o código
 * correspondente.
 */
@SuppressWarnings("restriction")
public class LayerSyncView extends ViewPart {

	// --- UI Text Constants ---
	private static final String FACADE_CLASS_LABEL = "Facade Class:";
	private static final String BUSINESS_CLASS_LABEL = "Business Class:";
	private static final String DATA_PROVIDER_CLASS_LABEL = "DataProvider Class:";
	private static final String METHOD_SIGNATURE_LABEL = "Method Signature:";
	private static final String GENERATE_BUTTON_TEXT = "Gerar Método em Cascata";
	private static final String SEARCH_BUTTON_TOOLTIP = "Procurar classe %s";
	private static final String TYPE_SELECTION_DIALOG_TITLE = "Selecionar Classe";
	private static final String TYPE_SELECTION_DIALOG_MESSAGE = "Digite o nome da classe (use * e ? como wildcards):";
	private static final String INFO_LABEL_TEXT = "💡 Preencha as classes e a assinatura. O método será criado em todas as camadas informadas.";

	// --- UI Widgets ---
	private Text facadeText;
	private Text businessText;
	private Text dataProviderText;
	private Text methodText;
	private Text returnTypeText;
	private Text parametersText;

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(3, false));

		facadeText = createTypeSelectionRow(parent, FACADE_CLASS_LABEL);
		businessText = createTypeSelectionRow(parent, BUSINESS_CLASS_LABEL);
		dataProviderText = createTypeSelectionRow(parent, DATA_PROVIDER_CLASS_LABEL);

		createMethodSignatureRow(parent);
		createGenerateButton(parent);
		createInfoLabel(parent);
	}

	private void createMethodSignatureRow(Composite parent) {
		new Label(parent, SWT.NONE).setText(METHOD_SIGNATURE_LABEL);

		Composite signatureComposite = new Composite(parent, SWT.NONE);
		signatureComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		GridLayout layout = new GridLayout(5, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		signatureComposite.setLayout(layout);

		returnTypeText = new Text(signatureComposite, SWT.BORDER);
		returnTypeText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		returnTypeText.setMessage("void");

		methodText = new Text(signatureComposite, SWT.BORDER);
		methodText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		methodText.setMessage("methodName");

		new Label(signatureComposite, SWT.NONE).setText("(");

		parametersText = new Text(signatureComposite, SWT.BORDER);
		parametersText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		parametersText.setMessage("String name, List<User> users");

		new Label(signatureComposite, SWT.NONE).setText(")");
	}

	/**
	 * Manipula o evento de clique no botão de geração de método.
	 */
	private void handleGenerate() {
		// Coleta os dados de TODAS as camadas
		String facadeClassName = facadeText.getText().trim();
		String businessClassName = businessText.getText().trim();
		String dataProviderClassName = dataProviderText.getText().trim();

		String methodName = methodText.getText().trim();
		String returnType = returnTypeText.getText().trim();
		String parametersString = parametersText.getText().trim();

		// Validação mínima: a camada Facade e a assinatura do método são obrigatórias
		if (facadeClassName.isEmpty() || methodName.isEmpty() || returnType.isEmpty()) {
			MessageDialog.openError(getSite().getShell(), "Campos Obrigatórios",
					"Por favor, preencha pelo menos a classe Facade e a assinatura completa do método.");
			return;
		}

		try {
			CodeGenerator generator = new CodeGenerator();
			// Chama o novo método que lida com todas as camadas
			generator.generateMethodsForAllLayers(facadeClassName, businessClassName, dataProviderClassName, methodName,
					returnType, parametersString);

			MessageDialog.openInformation(getSite().getShell(), "Sucesso",
					"Método '" + methodName + "' foi gerado com sucesso nas camadas selecionadas.");

		} catch (Exception e) {
			MessageDialog.openError(getSite().getShell(), "Erro na Geração de Código",
					"Falha ao gerar o método: " + e.getMessage());
			e.printStackTrace(); // Log para o console do Eclipse
		}
	}

	// --- O resto da classe permanece o mesmo ---

	@Override
	public void setFocus() {
		if (facadeText != null && !facadeText.isDisposed()) {
			facadeText.setFocus();
		}
	}

	private Text createTypeSelectionRow(Composite parent, String labelText) {
		new Label(parent, SWT.NONE).setText(labelText);
		Text textWidget = new Text(parent, SWT.BORDER);
		textWidget.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button searchButton = new Button(parent, SWT.PUSH);
		searchButton.setText("🔍");
		String simpleClassName = labelText.replace(" Class:", "");
		searchButton.setToolTipText(String.format(SEARCH_BUTTON_TOOLTIP, simpleClassName));
		searchButton.addListener(SWT.Selection, e -> handleTypeSelection(textWidget));
		return textWidget;
	}

	private void createGenerateButton(Composite parent) {
		Button generateButton = new Button(parent, SWT.PUSH);
		generateButton.setText(GENERATE_BUTTON_TEXT);
		generateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));
		generateButton.addListener(SWT.Selection, e -> handleGenerate());
	}

	private void createInfoLabel(Composite parent) {
		Label infoLabel = new Label(parent, SWT.WRAP);
		infoLabel.setText(INFO_LABEL_TEXT);
		GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
		gridData.verticalIndent = 10;
		infoLabel.setLayoutData(gridData);
	}

	private void handleTypeSelection(Text targetField) {
		Shell shell = getSite().getShell();
		FilteredTypesSelectionDialog dialog = new FilteredTypesSelectionDialog(shell, false,
				getSite().getWorkbenchWindow(), SearchEngine.createWorkspaceScope(), IJavaSearchConstants.TYPE);
		dialog.setTitle(TYPE_SELECTION_DIALOG_TITLE);
		dialog.setMessage(TYPE_SELECTION_DIALOG_MESSAGE);
		if (dialog.open() != Window.OK) {
			return;
		}
		Object[] result = dialog.getResult();
		if (result == null || result.length <= 0 || !(result[0] instanceof IType)) {
			return;
		}
		IType type = (IType) result[0];
		targetField.setText(type.getFullyQualifiedName());
	}
}