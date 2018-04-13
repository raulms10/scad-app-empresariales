package controllers

import javax.inject._
import play.api._
import play.api.mvc._

// Importes necesarios para que esta clase funcione
import play.api.libs.json._
import models.Pet
import play.api.db._ // Este es especialmente necesario para conectarse con la BD

// Controlador de la pagina web
// NOTA: No olvidar poner >>> db: Database como parametro de la clase. OJO!
@Singleton
class HomeController @Inject()(db: Database, cc: ControllerComponents) extends AbstractController(cc)
{
  // Metodo para recuperar los datos de la agencia
  def getAgencyInfo = Action {
    
  }
  
  // Metodo para recuperar todos los inmuebles de la agencia
  def getAll = Action {
    
  }
  
  // Metodo para recuperar los inmuebles indicados en el json del cuerpo
  def search = Action {
    
  }

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
