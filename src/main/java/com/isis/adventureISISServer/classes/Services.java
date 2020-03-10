/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.isis.adventureISISServer.classes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author alexa
 */
public class Services {

    public World getWorld(String username) throws JAXBException {
        World world = readWorldFromXml(username);
        return world;
    }

    public World readWorldFromXml(String username) throws JAXBException {
        String fileName = username + "-world.xml";
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Unmarshaller u = cont.createUnmarshaller();
        World world;
        try {
            File worldFile = new File(fileName);
            world = (World) u.unmarshal(worldFile);
        } catch (Exception e) {
            InputStream input = getClass().getClassLoader().getResourceAsStream("world.xml");
            world = (World) u.unmarshal(input);
        }
        return world;
    }

    void saveWorldToXml(World world, String username) throws FileNotFoundException, JAXBException {
        String fileName = username + "-world.xml";
        OutputStream output = new FileOutputStream(fileName);
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Marshaller m = cont.createMarshaller();
        m.marshal(world, output);
    }

    public Boolean updateProduct(String username, ProductType newproduct) throws JAXBException, FileNotFoundException {
// aller chercher le monde qui correspond au joueur
        World world = getWorld(username);
// trouver dans ce monde, le produit équivalent à celui passé
// en paramètre
        ProductType product = findProductById(world, newproduct.getId());
        if (product == null) 
        {return false;}
// calculer la variation de quantité. Si elle est positive c'est
// que le joueur a acheté une certaine quantité de ce produit
// sinon c’est qu’il s’agit d’un lancement de production.

        int qtchange = newproduct.getQuantite() - product.getQuantite();
        if (qtchange > 0) {
// soustraire del'argent du joueur le cout de la quantité
// achetée et mettre à jour la quantité de product 
        } else {
            
// initialiser product.timeleft à product.vitesse
// pour lancer la production
        }
// sauvegarder les changements du monde
        saveWorldToXml(world, username);
        return true;
    }

    private ProductType findProductById(World world, int id) {
        ProductType produit = null;
        List<ProductType> p = world.getProducts().getProduct();
        for (ProductType pr : p) {
            int idp = pr.getId();
            if (idp == id) {
                produit = pr;
                return produit;
            }
        }
        return produit;
    }

}