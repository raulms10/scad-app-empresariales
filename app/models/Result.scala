package models

// Importes necesarios para hacer el modelo o POJO
import com.sun.org.apache.xpath.internal.operations.Or
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

// Se crea la clase Result
case class Result(status: String, body: String)

// Y se crea un objeto Result con el fin de implementar los metodos para leer/escribir Result como si fuera un Json
object Result
{
  implicit val resultWrite = Json.writes[Result]
  implicit val resultRead = Json.reads[Result]
}