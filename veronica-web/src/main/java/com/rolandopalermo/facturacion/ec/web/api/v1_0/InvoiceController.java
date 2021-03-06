package com.rolandopalermo.facturacion.ec.web.api.v1_0;

import static com.rolandopalermo.facturacion.ec.common.util.Constantes.API_DOC_ANEXO_1;

import java.io.ByteArrayInputStream;

import javax.validation.Valid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rolandopalermo.facturacion.ec.bo.v1_0.InvoiceBO;
import com.rolandopalermo.facturacion.ec.bo.v1_0.RideBO;
import com.rolandopalermo.facturacion.ec.bo.v1_0.SriBO;
import com.rolandopalermo.facturacion.ec.common.exception.InternalServerException;
import com.rolandopalermo.facturacion.ec.common.exception.VeronicaException;
import com.rolandopalermo.facturacion.ec.dto.v1_0.VeronicaResponseDTO;
import com.rolandopalermo.facturacion.ec.dto.v1_0.invoice.FacturaDTO;
import com.rolandopalermo.facturacion.ec.dto.v1_0.invoice.FacturaIdDTO;
import com.rolandopalermo.facturacion.ec.dto.v1_0.sri.RespuestaComprobanteDTO;
import com.rolandopalermo.facturacion.ec.dto.v1_0.sri.RespuestaSolicitudDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
@RequestMapping(value = "/api/v1.0/facturas")
@Api(description = "Gestiona el ciclo de vida de una factura electrónica")
public class InvoiceController {

	@Autowired
	private InvoiceBO invoiceBO;

	@Autowired
	private SriBO sriBO;

	@Autowired
	private RideBO rideBO;

	private static final Logger logger = LogManager.getLogger(SriBO.class);

	@ApiOperation(value = "Crea una factura electrónica y la almacena en base de datos")
	@PostMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> createInvoice(@Valid @ApiParam(value = API_DOC_ANEXO_1, required = true) @RequestBody FacturaDTO facturaDTO) {
		try {
			VeronicaResponseDTO<Object> response = new VeronicaResponseDTO<>();
			FacturaIdDTO facturaIdDTO;
			facturaIdDTO = invoiceBO.createInvoice(facturaDTO);
			response.setSuccess(true);
			response.setResult(facturaIdDTO);
			return new ResponseEntity<>(response, HttpStatus.CREATED);
		} catch (VeronicaException e) {
			logger.error("createInvoice", e);
			throw new InternalServerException(e.getMessage());
		}
	}

	@ApiOperation(value = "Envía una factura electrónica al SRI y actualiza su estado en base de datos")
	@PutMapping(value = "{claveAcceso}/enviar", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> postInvoice(@Valid @ApiParam(value = "Clave de acceso del comprobante electrónico", required = true) @PathVariable String claveAcceso) {
		VeronicaResponseDTO<Object> response = new VeronicaResponseDTO<>();
		RespuestaSolicitudDTO respuestaSolicitudDTO = sriBO.enviarComprobante(claveAcceso);
		response.setSuccess(true);
		response.setResult(respuestaSolicitudDTO);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@ApiOperation(value = "Autoriza una factura electrónica y actualiza su estado en base de datos")
	@PutMapping(value = "{claveAcceso}/autorizar", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> applyInvoice(@Valid @ApiParam(value = "Clave de acceso del comprobante electrónico", required = true) @PathVariable String claveAcceso) {
		try {
			VeronicaResponseDTO<Object> response = new VeronicaResponseDTO<>();
			RespuestaComprobanteDTO respuestaComprobanteDTO = sriBO.autorizarComprobante(claveAcceso);
			response.setSuccess(true);
			response.setResult(respuestaComprobanteDTO);
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception e) {
			logger.error("applyInvoice", e);
			throw new InternalServerException(e.getMessage());
		}
	}

	@ApiOperation(value = "Elimina una factura de la base de datos")
	@DeleteMapping(value = "{claveAcceso}/eliminar", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> deleteInvoice(@Valid @ApiParam(value = "Clave de acceso del comprobante electrónico", required = true) @PathVariable String claveAcceso) {
		try {
			VeronicaResponseDTO<Object> response = new VeronicaResponseDTO<>();
			invoiceBO.deleteInvoice(claveAcceso);
			response.setSuccess(true);
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception e) {
			logger.error("applyInvoice", e);
			throw new InternalServerException(e.getMessage());
		}
	}

	@ApiOperation(value = "Retorna la representación PDF de una factura electrónica")
	@GetMapping(value = "{claveAcceso}/archivos/pdf")
	public ResponseEntity<Object> generateRIDE(@Valid @ApiParam(value = "Clave de acceso del comprobante electrónico", required = true) @PathVariable("claveAcceso") String claveAcceso) {
		try {
			VeronicaResponseDTO<Object> response = new VeronicaResponseDTO<>();
			byte[] pdfContent = rideBO.generateRIDE(claveAcceso);
			response.setSuccess(true);
			HttpHeaders headers = new HttpHeaders();
			headers.add("content-disposition", "inline; filename=" + claveAcceso + ".pdf");
			headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
			headers.add("Pragma", "no-cache");
			headers.add("Expires", "0");
			return ResponseEntity.ok().headers(headers).contentLength(pdfContent.length).contentType(MediaType.parseMediaType("application/octet-stream")).body(new InputStreamResource(new ByteArrayInputStream(pdfContent)));
		} catch (Exception e) {
			logger.error("generateRIDE", e);
			throw new InternalServerException(e.getMessage());
		}
	}

	@ApiOperation(value = "Retorna la representación XML de una factura electrónica")
	@GetMapping(value = "{claveAcceso}/archivos/xml", produces = { MediaType.APPLICATION_XML_VALUE }, headers = "Accept=application/xml")
	public ResponseEntity<Object> getXML(@Valid @ApiParam(value = "Clave de acceso del comprobante electrónico", required = true) @PathVariable("claveAcceso") String claveAcceso) {
		try {
			return ResponseEntity.ok(invoiceBO.getXML(claveAcceso));
		} catch (Exception e) {
			logger.error("generateRIDE", e);
			throw new InternalServerException(e.getMessage());
		}
	}

}