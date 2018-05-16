package models

// Importes necesarios para hacer el modelo o POJO
import com.sun.org.apache.xpath.internal.operations.Or
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

// Se crea la clase Answer
case class Answer(status: String, body: String)

// Y se crea un objeto Answer con el fin de implementar los metodos para leer/escribir Answer como si fuera un Json
object Answer
{
  implicit val resultWrite = Json.writes[Answer]
  implicit val resultRead = Json.reads[Answer]
}