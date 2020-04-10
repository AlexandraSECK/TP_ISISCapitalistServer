/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.isis.adventureISISServer.classes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
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

    public World getWorld(String username) throws JAXBException, FileNotFoundException {
        //Récupère le monde 
        World world = readWorldFromXml(username);
        long d1 = System.currentTimeMillis();
        long d2 = world.getLastupdate();

        //Si du temps c'est écoulé : mise à jour du monde
        if (d1 != d2) {
            majWorld(world);
            world.setLastupdate(System.currentTimeMillis());
        }
        return world;
    }

    public World readWorldFromXml(String username) throws JAXBException, FileNotFoundException {
        String fileName = username + "-world.xml";
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Unmarshaller u = cont.createUnmarshaller();
        World world;
        //Si le monde existe déjà, le recupérer sinon renvoyer un nouveau monde
        try {
            File worldFile = new File(fileName);
            InputStream targetStream = new FileInputStream(worldFile);
            world = (World) u.unmarshal(targetStream);

        } catch (Exception e) {
            InputStream input = getClass().getClassLoader().getResourceAsStream("world.xml");
            world = (World) u.unmarshal(input);
            saveWorldToXml(world, username);
            System.out.println(e);
        }
        return world;
    }

    //Sauvegarde du nouveau monde dans le fichier xml
    void saveWorldToXml(World world, String username) throws FileNotFoundException, JAXBException {
        String fileName = username + "-world.xml";
        OutputStream output = new FileOutputStream(fileName);
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Marshaller m = cont.createMarshaller();
        m.marshal(world, output);
    }

    //Prise en compte d'un achat ou production de produit
    public Boolean updateProduct(String username, ProductType newproduct) throws JAXBException, FileNotFoundException, Exception {
        // Cherche le monde qui correspond au joueur
        World world = getWorld(username);
        // Trouve dans ce monde, le produit équivalent à celui passé en paramètre
        ProductType product = findProductById(world, newproduct.getId());
        if (product == null) {
            return false;
        }
        //Verifie si c'est un achat de produit (qtechange>0)
        int qtchange = newproduct.getQuantite() - product.getQuantite();
        if (qtchange > 0) {
            int ancienneqte = product.getQuantite();
            double ancienPrix = product.cout;
            double q = product.getCroissance();
            double coutTotal = ancienPrix * (1 - (Math.pow(q, qtchange))) / (1 - q);
            double argent = world.getMoney() - coutTotal;
            //Retire le cout de l'achat des produits et maj du cout et de la quantité   
            world.setMoney(argent);
            product.setQuantite(newproduct.getQuantite());
            product.setCout(Math.pow(product.getCroissance(), qtchange) * ancienPrix);

            //Si c'est pas le premier achat, modifie le revenu du produit
            if (ancienneqte != 0) {
                double newRevenu = (product.getRevenu() / ancienneqte) * product.getQuantite();
                product.setRevenu(newRevenu);
            }
        } else {
            //Si c'est une production ( si product.timeleft!=0, possible décalage entre serveur et client.
            //Le client ne peut pas commencer de production si celle d'avant pas terminée donc on va estimer que c'est le cas
            if (product.timeleft != 0) {
                double argentGagne = (product.getRevenu()) * (1 + world.getActiveangels() * world.getAngelbonus() / 100);
                world.setMoney(world.getMoney() + argentGagne);
                world.setScore(world.getScore() + argentGagne);
                product.setTimeleft(0);
            }
            //Debut de la production 
            product.timeleft = product.vitesse;
        }

        //Verifie si un unlock a été débloqué
        List<PallierType> listeUnlock = product.getPalliers().getPallier();
        for (PallierType p : listeUnlock) {
            if (!p.isUnlocked() && product.getQuantite() >= p.getSeuil()) {
                majPallier(p, product);
            }
        }
        // sauvegarder les changements du monde
        saveWorldToXml(world, username);
        return true;
    }

    //Récupère le produit en fonction de son id
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

// renvoie false si l’action n’a pas pu être traitée
    public Boolean updateManager(String username, PallierType newmanager) throws JAXBException, FileNotFoundException {
        // Cherche le monde qui correspond au joueur
        World world = getWorld(username);
        // Trouve dans ce monde, le manager équivalent à celui passé en paramètre
        PallierType manager = findManagerByName(world, newmanager.getName());
        if (manager == null) {
            return false;
        }
        // débloquer le manager 
        manager.setUnlocked(true);

        ProductType product = findProductById(world, manager.getIdcible());
        if (product == null) {
            return false;
        }
        //Debloque le manager du produit
        product.setManagerUnlocked(true);
        // soustrait de l'argent du joueur le cout du manager
        // sauvegarder les changements au monde
        double argent = world.getMoney() - manager.getSeuil();
        world.setMoney(argent);
        saveWorldToXml(world, username);
        return true;
    }

    // Si une Amélioration est acheté
    public Boolean updateUpgrade(String username, PallierType upgrade) throws JAXBException, FileNotFoundException {
        // Cherche le monde qui correspond au joueur
        World world = getWorld(username);
        //Si le joueur a assez d'argent et que l'amélioration n'a pas déjà été débloquée
        if (world.getMoney() >= upgrade.getSeuil() && !upgrade.isUnlocked()) {
            //Si jamais id cible=0 alors l'amélioration concerne tous les produits
            if (upgrade.getIdcible() == 0) {
                List<ProductType> listeProduits = world.getProducts().getProduct();
                for (ProductType p : listeProduits) {
                    majPallier(upgrade, p);
                }
                return true;
            } else {
                //Sinon on récupère l'id du produit et on lui applique le pallier
                ProductType p = findProductById(world, upgrade.getIdcible());
                majPallier(upgrade, p);
                return true;
            }
        }
        return false;
    }

    //Cherche le manager en fonction de son nom
    private PallierType findManagerByName(World world, String name) {
        PallierType manager = null;
        List<PallierType> p = world.getManagers().getPallier();
        for (PallierType pa : p) {
            String mName = pa.getName();
            if (name.equals(mName)) {
                manager = pa;
                return manager;
            }
        }
        return manager;
    }

    //Mise à jour du monde 
    public void majWorld(World world) {
        //Vérifie si des produits ont été produit
        List<ProductType> ListProduit = world.getProducts().getProduct();
        long d1 = System.currentTimeMillis();
        long d2 = world.getLastupdate();
        long delta = d1 - d2;
        double angesActifs = world.getActiveangels();
        double angelBonus = world.getAngelbonus();
        //Pour chaque produit 
        for (ProductType pr : ListProduit) {
            double argentGagne = pr.getRevenu() * (1 + (angesActifs * angelBonus / 100));
            //Si le manager a été débloqué
            if (pr.isManagerUnlocked()) {
                //Calcul combien de produits ont pu être produit et set le temps restant
                int tempsProduit = pr.getVitesse();
                int nbrProduit = (int) (delta / tempsProduit);
                long tpsRestant = tempsProduit - (delta % tempsProduit);
                pr.setTimeleft(tpsRestant);

                //calcule l'argent gagné et set le score et money
                argentGagne = argentGagne * nbrProduit;
                world.setMoney(world.getMoney() + argentGagne);
                world.setScore(world.getScore() + argentGagne);
            } else {
                //Si pas de manager, verifie si une production était en cours et met a jour le score
                if (pr.getTimeleft() != 0 && delta > pr.getTimeleft()) {
                    world.setMoney(world.getMoney() + argentGagne);
                    world.setScore(world.getScore() + argentGagne);
                    pr.setTimeleft(0);

                } else {
                    //Si la production n'est pas terminée, met le temps restant à jour
                    if (pr.getTimeleft() != 0) {
                        long newTimeLeft = pr.getTimeleft() - delta;
                        pr.setTimeleft(newTimeLeft);
                    }
                }
            }
        }
    }

   //Prise en compte des palliers
    public void majPallier(PallierType p, ProductType product) {
        p.setUnlocked(true);
        //Si c'est une vitesse, reduit le vitesse en fonction du ratio
        if (p.getTyperatio() == TyperatioType.VITESSE) {
            double v = product.getVitesse();
            int newv = (int) (v / p.getRatio());
            product.setVitesse(newv);

        } else {
            //Si c'est un gain, multiplie le revenu par le ratio
            if (p.getTyperatio() == TyperatioType.GAIN) {
                double r = product.getRevenu();
                r = r * p.getRatio();
                product.setRevenu(r);
            }
        }
    }

    //Reset du monde
    public World deleteWorld(String username) throws JAXBException, FileNotFoundException {
        //Recupère le monde du joueur
        World world = getWorld(username);
        double activesAngels = world.getActiveangels();
        double totalAngels = world.getTotalangels();
        double angesGagnes = nbAnges(world);
        double Score = world.getScore();
        //Ajoute les anges aux anges actifs et au total
        activesAngels += angesGagnes;
        totalAngels += angesGagnes;
    
        //Récupère un nouveau monde
        InputStream input = getClass().getClassLoader().getResourceAsStream("world.xml");
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Unmarshaller u = cont.createUnmarshaller();
        World NewWorld = (World) u.unmarshal(input);
        //Met à jour les anges et le score
        NewWorld.setTotalangels(totalAngels);
        NewWorld.setActiveangels(activesAngels);
        NewWorld.setScore(Score);
        //Sauvegarde le nouveau monde
        return NewWorld;
    }

    
    //Calcule les anges gagnés
    public double nbAnges(World world) {
        double angelToClaim = world.getTotalangels();
        double score = world.getScore();
        angelToClaim = Math.round(150 * Math.sqrt(score / Math.pow(10, 7))) - angelToClaim;
        System.out.println("Ange gagnés" + angelToClaim);
        return angelToClaim;
    }

   //Prise en compte des angels upgrade 
    public void angelUpgrade(String username, PallierType angelUpgrade) throws JAXBException, FileNotFoundException {
   //Récupère le monde du joueyr
        World world = getWorld(username);
        double prix = angelUpgrade.getSeuil();
        double angesActif = world.getActiveangels();
        //Enleve aux anges actif le cout 
        angesActif -= prix;
        
        //Si c'est un type ange modifie le bonus
        if (angelUpgrade.getTyperatio() == TyperatioType.ANGE) {
            int bonus = world.getAngelbonus();
            bonus += angelUpgrade.getRatio();
            world.setAngelbonus(bonus);

        } else {
            //S c'est un type vitesse ou gain
            updateUpgrade(username, angelUpgrade);
        }
        world.setActiveangels(angesActif);
        saveWorldToXml(world, username);
    }
}
