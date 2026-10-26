package com.tovalh.finmail.config;

import com.tovalh.finmail.model.Categoria;
import com.tovalh.finmail.model.Configuracion;
import com.tovalh.finmail.repository.CategoriaRepository;
import com.tovalh.finmail.repository.ConfiguracionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DataSeeder {

    private final CategoriaRepository categoriaRepository;
    private final ConfiguracionRepository configuracionRepository;

    public DataSeeder(CategoriaRepository categoriaRepository, ConfiguracionRepository configuracionRepository) {
        this.categoriaRepository = categoriaRepository;
        this.configuracionRepository = configuracionRepository;
    }

    record CategoriaDefault(String color, String icono) {}

    private static final Map<String, CategoriaDefault> CATEGORIAS = Map.ofEntries(
            Map.entry("Alimentación",      new CategoriaDefault("#F97316", "UtensilsCrossed")),
            Map.entry("Supermercado",      new CategoriaDefault("#EAB308", "ShoppingCart")),
            Map.entry("Restaurantes",      new CategoriaDefault("#EF4444", "ChefHat")),
            Map.entry("Transporte",        new CategoriaDefault("#3B82F6", "Bus")),
            Map.entry("Combustible",       new CategoriaDefault("#F59E0B", "Fuel")),
            Map.entry("Salud y Farmacia",  new CategoriaDefault("#10B981", "HeartPulse")),
            Map.entry("Entretenimiento",   new CategoriaDefault("#8B5CF6", "Clapperboard")),
            Map.entry("Suscripciones",     new CategoriaDefault("#6366F1", "RefreshCw")),
            Map.entry("Ropa y Calzado",    new CategoriaDefault("#EC4899", "Shirt")),
            Map.entry("Hogar y Servicios", new CategoriaDefault("#14B8A6", "House")),
            Map.entry("Educación",         new CategoriaDefault("#0EA5E9", "GraduationCap")),
            Map.entry("Viajes",            new CategoriaDefault("#F43F5E", "Plane")),
            Map.entry("Delivery",          new CategoriaDefault("#FB923C", "PackageOpen")),
            Map.entry("Otro",              new CategoriaDefault("#6B7280", "Tag"))
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (configuracionRepository.findById(1L).isEmpty()) {
            configuracionRepository.save(Configuracion.builder().id(1L).diaInicioMes(9).build());
        }

        CATEGORIAS.forEach((nombre, defaults) -> {
            if (categoriaRepository.findByNombre(nombre).isEmpty()) {
                categoriaRepository.save(Categoria.builder()
                        .nombre(nombre)
                        .color(defaults.color())
                        .icono(defaults.icono())
                        .build());
            }
        });
    }
}