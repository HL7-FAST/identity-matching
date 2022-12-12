package ca.uhn.fhir.jpa.starter.operations.models;


public class IdentifierQueryParams {

	private String _identifierSystem;
	private String _identifierValue;
	private String _identifierCode;

	public IdentifierQueryParams() {}

	public IdentifierQueryParams(String system, String value, String code)
	{
		_identifierSystem = system;
		_identifierValue = value;
		_identifierCode = code;
	}

	public String getIdentifierSystem() { return _identifierSystem; }
	public void setIdentifierSystem(String system) { this._identifierSystem = system; }

	public String getIdentifierValue() { return _identifierValue; }
	public void setIdentifierValue(String value) { this._identifierValue = value; }

	public String getIdentifierCode() { return _identifierCode; }
	public void setIdentifierCode(String code) { this._identifierCode = code; }

}
