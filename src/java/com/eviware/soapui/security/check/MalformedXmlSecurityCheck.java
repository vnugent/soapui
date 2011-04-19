/*
 *  soapUI, copyright (C) 2004-2011 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.security.check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.MalformedXmlAttributeConfig;
import com.eviware.soapui.config.MalformedXmlConfig;
import com.eviware.soapui.config.SecurityCheckConfig;
import com.eviware.soapui.config.StrategyTypeConfig;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.security.SecurityCheckedParameter;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.security.SecurityTestRunContext;
import com.eviware.soapui.security.SecurityTestRunner;
import com.eviware.soapui.security.ui.MalformedXmlAdvancedSettingsPanel;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.soapui.support.xml.XmlObjectTreeModel;
import com.eviware.soapui.support.xml.XmlObjectTreeModel.XmlTreeNode;
import com.eviware.soapui.support.xml.XmlUtils;

public class MalformedXmlSecurityCheck extends AbstractSecurityCheckWithProperties
{

	public static final String TYPE = "MalformedXmlSecurityCheck";
	public static final String NAME = "Malformed Xml";
	private Map<SecurityCheckedParameter, ArrayList<String>> parameterMutations = new HashMap<SecurityCheckedParameter, ArrayList<String>>();
	private boolean mutation;
	private MalformedXmlConfig malformedXmlConfig;
	private MalformedXmlAttributeConfig malformedAttributeConfig;
	private MalformedXmlAdvancedSettingsPanel advancedSettingsPanel;

	public MalformedXmlSecurityCheck( TestStep testStep, SecurityCheckConfig config, ModelItem parent, String icon )
	{
		super( testStep, config, parent, icon );
		if( config.getConfig() == null || !( config.getConfig() instanceof MalformedXmlConfig ) )
			initMalformedXmlConfig();
		else
		{
			malformedXmlConfig = ( ( MalformedXmlConfig )config.getConfig() );
			malformedAttributeConfig = malformedXmlConfig.getAttributeMutation();
		}
	}

	/**
	 * Default malformed xml configuration
	 */
	private void initMalformedXmlConfig()
	{
		getConfig().setConfig( MalformedXmlConfig.Factory.newInstance() );
		malformedXmlConfig = ( MalformedXmlConfig )getConfig().getConfig();

		malformedXmlConfig.addNewAttributeMutation();

		// init default configuration
		malformedXmlConfig.setInsertNewElement( true );
		malformedXmlConfig.setNewElementValue( "<xml>xml <joke> </xml> </joke>" );
		malformedXmlConfig.setChangeTagName( true );
		malformedXmlConfig.setLeaveTagOpen( true );

		malformedAttributeConfig = malformedXmlConfig.getAttributeMutation();
		malformedAttributeConfig.setMutateAttributes( true );
		malformedAttributeConfig.setInsertInvalidChars( true );
		malformedAttributeConfig.setLeaveAttributeOpen( true );
		malformedAttributeConfig.setAddNewAttribute( true );
		malformedAttributeConfig.setNewAttributeName( "newAttribute" );
		malformedAttributeConfig.setNewAttributeValue( "XXX" );
	}

	@Override
	protected void execute( SecurityTestRunner runner, TestStep testStep, SecurityTestRunContext context )
	{
		try
		{
			StringToStringMap paramsUpdated = update( testStep, context );
			MessageExchange message = ( MessageExchange )testStep.run( ( TestCaseRunner )runner, context );
			createMessageExchange( paramsUpdated, message );
		}
		catch( XmlException e )
		{
			SoapUI.logError( e, "[MalformedXmlSecurityCheck]XPath seems to be invalid!" );
			reportSecurityCheckException( "Propety value is not XML or XPath is wrong!" );
		}
		catch( Exception e )
		{
			SoapUI.logError( e, "[MalformedXmlSecurityCheck]Property value is not valid xml!" );
			reportSecurityCheckException( "Propety value is not XML or XPath is wrong!" );
		}
	}

	private StringToStringMap update( TestStep testStep, SecurityTestRunContext context ) throws XmlException, Exception
	{
		StringToStringMap params = new StringToStringMap();

		if( parameterMutations.size() == 0 )
			mutateParameters( testStep, context );

		if( getExecutionStrategy().getStrategy() == StrategyTypeConfig.ONE_BY_ONE )
		{
			/*
			 * Idea is to drain for each parameter mutations.
			 */
			for( SecurityCheckedParameter param : getParameterHolder().getParameterList() )
			{
				if( parameterMutations.containsKey( param ) )
					if( parameterMutations.get( param ).size() > 0 )
					{
						TestProperty property = getTestStep().getProperties().get( param.getName() );
						String value = context.expand( property.getValue() );
						if( param.getXpath() == null || param.getXpath().trim().length() == 0 )
						{
							// no xpath ignore
						}
						else
						{
							// no value, do nothing.
							if( value == null || value.trim().equals( "" ) )
								continue;
							XmlObjectTreeModel model = new XmlObjectTreeModel( property.getSchemaType().getTypeSystem(),
									XmlObject.Factory.parse( value ) );
							XmlTreeNode[] nodes = model.selectTreeNodes( context.expand( param.getXpath() ) );
							StringBuffer buffer = new StringBuffer( value );
							for( int cnt = 0; cnt < nodes.length; cnt++ )
							{
								// find right node
								int start = value.indexOf( "<" + nodes[cnt].getNodeName() ); // keeps
								// node
								// start
								int cnt2 = 0;
								while( cnt2 < cnt )
								{
									start = value.indexOf( "<" + nodes[cnt].getNodeName(), start );
									cnt2++ ;
								}
								String nodeXml = getXmlForNode( nodes[cnt] );
								int end = value.indexOf( "<" + nodes[cnt].getNodeName(), start + 1 );
								if( end <= 0 )
								{
									if( nodeXml.endsWith( "</" + nodes[cnt].getDomNode().getNodeName() + ">" ) )
									{
										end = value.indexOf( "</" + nodes[cnt].getDomNode().getNodeName() + ">" )
												+ ( "</" + nodes[cnt].getDomNode().getNodeName() + ">" ).length();
									}
									else
									{
										end = value.indexOf( ">", value.indexOf( "/", start ) );
									}
								}
								if( end <= 0 || end <= start )
									break;
								buffer.replace( start, end + 1, parameterMutations.get( param ).get( 0 ) );
							}
							params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
							parameterMutations.get( param ).remove( 0 );

							testStep.getProperties().get( param.getName() ).setValue( buffer.toString() );
						}

						break;
					}
			}
		}
		else
		{
			for( TestProperty property : testStep.getPropertyList() )
			{

				String value = context.expand( property.getValue() );
				if( XmlUtils.seemsToBeXml( value ) )
				{
					StringBuffer buffer = new StringBuffer( value );
					XmlObjectTreeModel model = null;
					model = new XmlObjectTreeModel( property.getSchemaType().getTypeSystem(), XmlObject.Factory
							.parse( value ) );
					for( SecurityCheckedParameter param : getParameterHolder().getParameterList() )
					{
						if( param.getXpath() == null || param.getXpath().trim().length() == 0 )
						{
							testStep.getProperties().get( param.getName() )
									.setValue( parameterMutations.get( param ).get( 0 ) );
							params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
							parameterMutations.get( param ).remove( 0 );
						}
						else
						{
							// no value, do nothing.
							if( value == null || value.trim().equals( "" ) )
								continue;
							if( param.getName().equals( property.getName() ) )
							{
								XmlTreeNode[] nodes = model.selectTreeNodes( context.expand( param.getXpath() ) );
								if( parameterMutations.containsKey( param ) )
									if( parameterMutations.get( param ).size() > 0 )
									{
										for( int cnt = 0; cnt < nodes.length; cnt++ )
										{
											// find right node
											int start = value.indexOf( "<" + nodes[cnt].getNodeName() ); // keeps
											// node
											// start
											int cnt2 = 0;
											while( cnt2 < cnt )
											{
												start = value.indexOf( "<" + nodes[cnt].getNodeName(), start );
												cnt2++ ;
											}
											String nodeXml = getXmlForNode( nodes[cnt] );
											int end = value.indexOf( "<" + nodes[cnt].getNodeName(), start + 1 );
											if( end <= 0 )
											{
												if( nodeXml.endsWith( "</" + nodes[cnt].getDomNode().getNodeName() + ">" ) )
												{
													end = value.indexOf( "</" + nodes[cnt].getDomNode().getNodeName() + ">" );
												}
												else
												{
													end = value.indexOf( ">", value.indexOf( "/", start ) );
												}
											}
											if( end <= 0 || end <= start )
												break;
											buffer.replace( start, end + 1, parameterMutations.get( param ).get( 0 ) );
										}
										params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
										parameterMutations.get( param ).remove( 0 );
									}
							}
						}
					}
					if( model != null )
						property.setValue( buffer.toString() );
				}

			}
		}
		return params;
	}

	private void mutateParameters( TestStep testStep, SecurityTestRunContext context ) throws XmlException, IOException
	{
		mutation = true;
		// for each parameter
		for( SecurityCheckedParameter parameter : getParameterHolder().getParameterList() )
		{
			if( parameter.isChecked() )
			{
				TestProperty property = testStep.getProperties().get( parameter.getName() );
				// check parameter does not have any xpath
				if( parameter.getXpath() == null || parameter.getXpath().trim().length() == 0 )
				{
					/*
					 * parameter xpath is not set ignore than ignore this parameter
					 */
				}
				else
				{
					// we have xpath but do we have xml which need to mutate
					// ignore if there is no value, since than we'll get exception
					if( !( property.getValue() == null && property.getDefaultValue() == null ) )
					{
						// get value of that property
						String value = context.expand( property.getValue() );

						// we have something that looks like xpath, or hope so.

						XmlObjectTreeModel model = new XmlObjectTreeModel( property.getSchemaType().getTypeSystem(),
								XmlObject.Factory.parse( value ) );
						XmlTreeNode[] nodes = model.selectTreeNodes( context.expand( parameter.getXpath() ) );

						if( nodes.length > 0 )
						{
							if( !parameterMutations.containsKey( parameter ) )
								parameterMutations.put( parameter, new ArrayList<String>() );
							parameterMutations.get( parameter ).addAll( mutateNode( nodes[0], value ) );
						}
					}

				}
			}
		}
	}

	private Collection<? extends String> mutateNode( XmlTreeNode node, String xml ) throws IOException
	{

		ArrayList<String> result = new ArrayList<String>();
		String nodeXml = getXmlForNode( node );
		// insert new element
		if( malformedXmlConfig.getInsertNewElement() )
		{
			StringBuffer buffer = new StringBuffer( nodeXml );
			if( nodeXml.endsWith( "</" + node.getDomNode().getNodeName() + ">" ) )
			{
				buffer.insert( nodeXml.indexOf( ">" ) + 1, malformedXmlConfig.getNewElementValue() );
			}
			else
			{
				buffer.delete( nodeXml.lastIndexOf( "/" ), nodeXml.length() );
				buffer
						.append( ">" + malformedXmlConfig.getNewElementValue() + "</" + node.getDomNode().getNodeName() + ">" );
			}
			result.add( buffer.toString() );
		}
		// change name
		if( malformedXmlConfig.getChangeTagName() )
		{
			String original = node.getNodeName();

			if( original.toUpperCase().equals( original ) )
			{
				result.add( nodeXml.replaceAll( original, original.toLowerCase() ) );
			}
			else if( original.toLowerCase().equals( original ) )
			{
				result.add( nodeXml.replaceAll( original, original.toUpperCase() ) );
			}
			else
			{
				StringBuffer buffer = new StringBuffer();
				// kewl
				for( char ch : original.toCharArray() )
				{
					if( Character.isUpperCase( ch ) )
						buffer.append( Character.toLowerCase( ch ) );
					else
						buffer.append( Character.toUpperCase( ch ) );
				}
				result.add( nodeXml.replaceAll( original, buffer.toString() ) );
			}

		}
		// leave tag open
		if( malformedXmlConfig.getLeaveTagOpen() )
		{
			StringBuffer buffer = new StringBuffer( nodeXml );
			if( nodeXml.endsWith( "</" + node.getDomNode().getNodeName() + ">" ) )
			{
				buffer.delete( buffer.indexOf( "</" + node.getDomNode().getNodeName() + ">" ), buffer.length() );
			}
			else
			{
				buffer.delete( nodeXml.lastIndexOf( "/" ), nodeXml.length() );
			}
			result.add( buffer.toString() );
		}
		if( malformedAttributeConfig.getMutateAttributes() )
		{
			if( malformedAttributeConfig.getAddNewAttribute() )
			{
				// insert new attribute just after node tag
				StringBuffer buffer = new StringBuffer( nodeXml );
				buffer.insert( node.getNodeName().length() + 1, " " + malformedAttributeConfig.getNewAttributeName() + "="
						+ "\"" + malformedAttributeConfig.getNewAttributeValue() + "\" " );
				result.add( buffer.toString() );
			}
			if( malformedAttributeConfig.getInsertInvalidChars() )
			{
				if( node.getDomNode().hasAttributes() )
				{
					// add it at beggining of attribute value
					StringBuffer buffer = new StringBuffer( nodeXml );
					buffer.insert( buffer.indexOf( "=" ) + 3, '"' );
					result.add( buffer.toString() );
				}
			}
			if( malformedAttributeConfig.getLeaveAttributeOpen() )
			{
				if( node.getDomNode().hasAttributes() )
				{
					StringBuffer buffer = new StringBuffer( nodeXml );
					buffer.delete( buffer.indexOf( "=" ) + 1, buffer.indexOf( "=" ) + 2 );
					result.add( buffer.toString() );
				}
			}
		}
		return result;
	}

	private String getXmlForNode( XmlTreeNode nodes )
	{
		XmlOptions options = new XmlOptions();
		options.setSaveOuter();
		options.setSavePrettyPrint();

		String xml = nodes.getXmlObject().xmlText( options );

		return XmlUtils.removeUnneccessaryNamespaces( xml );
	}

	@Override
	public String getConfigDescription()
	{
		return "Configures Malformed Xml Security Check";
	}

	@Override
	public String getConfigName()
	{
		return "Malformed Xml Security Check";
	}

	@Override
	public String getHelpURL()
	{
		return "http://www.soapui.org";
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	@Override
	protected boolean hasNext( TestStep testStep, SecurityTestRunContext context )
	{
		boolean hasNext = false;
		if( ( parameterMutations == null || parameterMutations.size() == 0 ) && !mutation )
		{
			if( getParameterHolder().getParameterList().size() > 0 )
				hasNext = true;
			else
				hasNext = false;
		}
		else
		{
			for( SecurityCheckedParameter param : parameterMutations.keySet() )
			{
				if( parameterMutations.get( param ).size() > 0 )
				{
					hasNext = true;
					break;
				}
			}
		}
		if( !hasNext )
		{
			parameterMutations.clear();
			mutation = false;
		}
		return hasNext;
	}

	@Override
	protected void clear()
	{
		parameterMutations.clear();
		mutation = false;
	}

	@Override
	public JComponent getAdvancedSettingsPanel()
	{
		if( advancedSettingsPanel == null )
			advancedSettingsPanel = new MalformedXmlAdvancedSettingsPanel( malformedXmlConfig );

		return advancedSettingsPanel.getPanel();
	}

}
